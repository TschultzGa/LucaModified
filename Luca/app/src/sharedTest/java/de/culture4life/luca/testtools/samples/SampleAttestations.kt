package de.culture4life.luca.testtools.samples

import de.culture4life.luca.util.encodeToBase64

class SampleAttestations {

    class Valid {
        private val expireNever = Int.MAX_VALUE

        private val header = """
            {
              "alg": "ES256"
            }
        """.trimIndent()

        private val claims = """
            {
              "iss": "luca-attestation",
              "os": "android",
              "exp": $expireNever
            }
        """

        private val signature = "not signed"

        fun deviceId() = "885376be-d7ab-4ae4-a786-02a507386c13"

        fun nonce() = "ORoyoXFp/azYui4fE57Ahz2j49/JqV47mKpsieR3So8="

        fun tokenAsJwt(): String {
            return "${shrinkToBase64(header)}.${shrinkToBase64(claims)}.${shrinkToBase64(signature)}"
        }
    }

    companion object {
        // Should match how backend will send the content (same string == same base64 result)
        private fun shrinkToBase64(content: String) = content
            .trimIndent()
            .replace("\n", "")
            .replace(" ", "")
            .toByteArray()
            .encodeToBase64()
    }
}
