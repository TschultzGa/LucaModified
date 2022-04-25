package de.culture4life.luca.document.provider.eudcc

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nexenio.rxkeystore.provider.signature.RxSignatureException
import de.culture4life.luca.BuildConfig
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.document.DocumentParsingException
import de.culture4life.luca.document.DocumentVerificationException
import de.culture4life.luca.document.DocumentVerificationException.Reason.INVALID_SIGNATURE
import de.culture4life.luca.document.provider.DocumentProvider
import de.culture4life.luca.util.decodeFromBase64
import dgca.verifier.app.decoder.base45.Base45Decoder
import dgca.verifier.app.decoder.base64ToX509Certificate
import dgca.verifier.app.decoder.cose.VerificationCryptoService
import dgca.verifier.app.decoder.model.VerificationResult
import dgca.verifier.app.decoder.services.X509
import dgca.verifier.app.decoder.toBase64
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.exceptions.CompositeException
import org.bouncycastle.asn1.*
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec

/**
 * Provider for the EU Digital COVID Certificate (EUDCC)
 */
@OptIn(ExperimentalUnsignedTypes::class)
open class EudccDocumentProvider(val context: Context) : DocumentProvider<EudccDocument>() {

    private val base45Decoder = Base45Decoder()
    private val decoder = EudccDecoder(base45Decoder)
    private val verifier = VerificationCryptoService(X509())

    override fun canParse(encodedData: String): Single<Boolean> {
        return Single.fromCallable {
            val coseData = decoder.decodeCoseData(encodedData)
            EudccSchemaValidator().validate(coseData.cbor)
        }.onErrorReturn { false }
    }

    override fun parse(encodedData: String): Single<EudccDocument> {
        return Single.fromCallable { EudccDocument(encodedData, decoder.decodeCertificate(encodedData)) }
            .map {
                it.apply {
                    document.provider = "EU Digital COVID Certificate"
                    document.isVerified = true
                }
            }
            .onErrorResumeNext { throwable ->
                if (throwable is DocumentParsingException) {
                    Single.error<DocumentParsingException>(throwable)
                }
                Single.error(DocumentParsingException(throwable))
            }
    }

    override fun verify(encodedData: String): Completable {
        return super.verify(encodedData)
            .andThen(
                if (BuildConfig.DEBUG) {
                    // allow documents with invalid signatures in debug builds for testing purposes
                    Completable.complete()
                } else {
                    verifySignature(encodedData)
                }
            )
    }

    fun verifySignature(encodedData: String): Completable {
        val validations = fetchSigningKeys(encodedData)
            .map { signingKey ->
                verifySignature(encodedData, signingKey)
                    .doOnSubscribe { Timber.d("Verifying signature using %s", signingKey) }
                    .doOnError { Timber.w("Signature verification failed: %s", it.toString()) }
                    .andThen(Observable.just(signingKey))
                    .onErrorResumeNext { Observable.empty() }
            }

        return Observable.mergeDelayError(validations)
            .firstOrError()
            .ignoreElement()
            .onErrorResumeNext {
                var throwable = it
                if (throwable is CompositeException) {
                    throwable = throwable.exceptions[0]
                }
                if (throwable is NoSuchElementException) {
                    Completable.error(DocumentVerificationException(INVALID_SIGNATURE, "Could not find a matching signing key"))
                } else {
                    Completable.error(DocumentVerificationException(INVALID_SIGNATURE, throwable))
                }
            }
    }

    private fun verifySignature(encodedData: String, signingKey: EudccSigningKey): Completable {
        return Completable.fromAction {
            val encodedCoseData = decoder.getEncodedCoseData(encodedData)
            val certificate = signingKey.rawData.base64ToX509Certificate()!!
            val verificationResult = VerificationResult()
            verifier.validate(encodedCoseData, certificate, verificationResult)
            if (!verificationResult.coseVerified) {
                throw IllegalArgumentException("Unable to verify COSE data")
            }
        }
    }

    private fun fetchSigningKeys(encodedData: String): Observable<EudccSigningKey> {
        return Single.fromCallable {
            val coseData = decoder.decodeCoseData(encodedData)
            coseData.kid?.toBase64()!!
        }.flatMapObservable { kid -> fetchSigningKeys().filter { it.kid == kid } }
    }

    open fun fetchSigningKeys(): Observable<EudccSigningKey> {
        return Single.defer {
            val networkManager = (context as LucaApplication).networkManager
            networkManager.initialize(context)
                .andThen(networkManager.getLucaEndpointsV4())
                .flatMap { it.eudccSigningKeys }
                .map { it.string() }
        }.flatMapObservable {
            val encodedSignature = it.lines()[0]
            val jsonString = it.lines()[1]
            verifySigningKeySignature(
                data = jsonString.toByteArray(),
                signature = toDERSignature(encodedSignature.decodeFromBase64())
            ).blockingAwait()
            val jsonObject = Gson().fromJson(jsonString, JsonObject::class.java)
            val certificates = jsonObject.getAsJsonArray("certificates")
            Observable.fromIterable(certificates)
        }.map {
            Gson().fromJson(it, EudccSigningKey::class.java)
        }.onErrorResumeNext { Observable.error(IllegalStateException("Unable fetch signing keys", it)) }
    }

    private fun verifySigningKeySignature(data: ByteArray, signature: ByteArray): Completable {
        return Single.fromCallable {
            val base64EncodedX509 = CERT_SERVER_PUBLIC_KEY
                .substringAfter("-----BEGIN PUBLIC KEY-----")
                .substringBefore("-----END PUBLIC KEY-----")
            val keySpec = X509EncodedKeySpec(base64EncodedX509.decodeFromBase64())
            return@fromCallable KeyFactory.getInstance("EC").generatePublic(keySpec)
        }.flatMapCompletable { publicKey ->
            Completable.defer {
                val cryptoManager = (context as LucaApplication).cryptoManager
                cryptoManager.initialize(context)
                    .andThen(cryptoManager.verifyEcdsa(data, signature, publicKey))
            }
        }.onErrorResumeNext { Completable.error(RxSignatureException("Unable to verify signing key signature", it)) }
    }

    private fun toDERSignature(tokenSignature: ByteArray): ByteArray {
        val r = tokenSignature.copyOfRange(0, tokenSignature.size / 2)
        val s = tokenSignature.copyOfRange(tokenSignature.size / 2, tokenSignature.size)
        val byteArrayOutputStream = ByteArrayOutputStream()
        val derOutputStream = ASN1OutputStream.create(byteArrayOutputStream, ASN1Encoding.DER)
        val vector = ASN1EncodableVector()
        vector.add(ASN1Integer(BigInteger(1, r)))
        vector.add(ASN1Integer(BigInteger(1, s)))
        derOutputStream.writeObject(DERSequence(vector))
        derOutputStream.close()
        return byteArrayOutputStream.toByteArray()
    }
}

private const val CERT_SERVER_PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----\n" +
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETHfi8foQF4UtSNVxSFxeu7W+gMxd\n" +
    "SGElhdo7825SD3Lyb+Sqh4G6Kra0ro1BdrM6Qx+hsUx4Qwdby7QY0pzxyA==\n" +
    "-----END PUBLIC KEY-----\n"
