package hu.kpark.trackingService.smsProcessing

object SmsProcessorFactory {
    // Returns the correct type of sms processor matching the phone prefix
    fun getProcessor(phoneNumber: String): SmsProcessor {
        return when {
            phoneNumber.startsWith("+3670") -> VodafoneSmsProcessor()
            else -> UnspecificSmsProcessor()
        }
    }
}