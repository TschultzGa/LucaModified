package de.culture4life.luca.testtools.samples

interface SampleLocations {

    val uuid: String
    val qrCodeContent: String

    interface CheckIn : SampleLocations {

        class Valid : CheckIn {
            override val uuid = "512875cb-17e6-4dad-ac62-3e792d94e03f"
            override val qrCodeContent = "https://app.luca-app.de/webapp/$uuid"
        }
    }

    interface Meeting : SampleLocations {

        class Valid : Meeting {
            override val uuid = "e4e3c...#e30"
            override val qrCodeContent = "https://app.luca-app.de/webapp/meeting/$uuid"
        }
    }
}
