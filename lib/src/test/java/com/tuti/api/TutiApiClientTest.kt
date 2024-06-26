package com.tuti.api


import com.tuti.api.authentication.SignInRequest
import com.tuti.api.data.TutiResponse
import com.tuti.api.ebs.EBSRequest
import com.tuti.api.ebs.NoebsTransfer
import com.tuti.model.KYC
import com.tuti.model.Ledger
import com.tuti.model.LedgerResponse
import com.tuti.model.NoebsTransaction
import com.tuti.model.NotificationFilters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class TutiApiClientTest {

    @Test
    fun getPaymentToken() {
        val tutiApiClient = TutiApiClient()
        val uuid = UUID.randomUUID()
        tutiApiClient.getPaymentToken(uuid.toString(), null!!, null!!)
    }

    @Test
    fun quickPayment() {
        val req = EBSRequest()

    }

    @Test
    fun generateOtpInsecure() {
        val tutiApiClient = TutiApiClient()
        var req: SignInRequest = SignInRequest(mobile = "sdsds")
        tutiApiClient.GenerateOtpInsecure(req, {}, { resp, t -> Unit })
    }

    @Test
    fun verifyOtp() {
        val tutiApiClient = TutiApiClient()
        var req = com.tuti.model.SignInRequest(mobile = "0912144343")
        tutiApiClient.VerifyOtp(req, null!!, null!!)
    }

    @Test
    fun addBeneficiary() {
//        val tutiApiClient = TutiApiClient()
//        tutiApiClient.authToken =
//            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJtb2JpbGUiOiIwMTExNDkzODg1IiwiZXhwIjoxNjY3NDMxNTY5LCJpc3MiOiJub2VicyJ9.DUyUJDTPO68b9f4Jl5dCnt-yIQOGfA94l2C-t7D88JY"
//        val beneficiary = Beneficiary(number = "0912141679", billType = 0, operator = 1)
//        tutiApiClient.addBeneficiary(beneficiary, { signInResponse: TutiResponse? ->
//            run { println(signInResponse) }
//        }, { objectReceived: TutiResponse?, exception: Exception? ->
//            run {
//                println(objectReceived.toString())
//            }
//        })
    }

    @Test
    fun upsertFirebase() {
        val tutiApiClient = TutiApiClient()
        tutiApiClient.authToken =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJtb2JpbGUiOiIwMTExNDkzODg1IiwiZXhwIjoxNjY3NDMxNTY5LCJpc3MiOiJub2VicyJ9.DUyUJDTPO68b9f4Jl5dCnt-yIQOGfA94l2C-t7D88JY"

        tutiApiClient.UpsertFirebase("this is my firebase token", { signInResponse: TutiResponse? ->
            run { println(signInResponse) }
        }, { objectReceived: TutiResponse?, _: Exception? ->
            run {
                println(objectReceived.toString())
            }
        })
    }

    @Test
    fun getNotifications() {
        val tutiApiClient = TutiApiClient()
        tutiApiClient.authToken =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJtb2JpbGUiOiIwMTExNDkzODg1IiwiZXhwIjoxNjY3NDMxNTY5LCJpc3MiOiJub2VicyJ9.DUyUJDTPO68b9f4Jl5dCnt-yIQOGfA94l2C-t7D88JY"
        tutiApiClient.getNotifications(
            NotificationFilters(getAll = true, mobile = "0111493885"),
            onResponse = { res ->
                run {
                    assertEquals(res[0].phone, "0129751986")
                    assertEquals(res[0].body, "fuff")
                }
            },
            onError = { objectReceived: TutiResponse?, _: Exception? ->
                run {

                }
            })
    }

//    @Test
//    fun getUserCard() {
//        val tutiApiClient = TutiApiClient()
//        tutiApiClient.authToken =
//            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJtb2JpbGUiOiIwMTExNDkzODg1IiwiZXhwIjoxNjY3NDMxNTY5LCJpc3MiOiJub2VicyJ9.DUyUJDTPO68b9f4Jl5dCnt-yIQOGfA94l2C-t7D88JY"
//        tutiApiClient.getUserCard(mobile="0111493885", onResponse = { res: User? ->
//            run {
//                assertEquals(res!!.cards[0].PAN, "0129751986")
//            }}, onError = { objectReceived: TutiResponse?, _: Exception? ->
//            run {
//
//            }
//        })
//    }

    @Test
    fun inquireNoebsWallet() {
        val tutiApiClient = TutiApiClient()
        tutiApiClient.authToken = "yourTestAuthToken"
        val expectedResponse = 121342212

        val latch = CountDownLatch(1) // Initialize CountDownLatch with count 1

        var responseReceived: Double? = null
        var errorOccurred: Exception? = null

        // Call the method
        tutiApiClient.inquireNoebsWallet("249_ACCT_1", { response ->
            responseReceived = response
            latch.countDown() // Decrease the count of the latch, releasing the wait in test
        }, { _, error ->
            errorOccurred = error
            latch.countDown() // Ensure to count down in case of error too
        })

        // Wait for the operation to complete or timeout
        val callCompleted = latch.await(10, TimeUnit.SECONDS)

        // Assertions
        if (callCompleted) {
            assertEquals(expectedResponse, responseReceived)
            assertNull(errorOccurred)
        } else {
            fail("The call to inquireNoebsWallet did not complete within the expected time.")
        }
    }

    @Test
    fun noebsTransfer() {
        val tutiApiClient = TutiApiClient()
        tutiApiClient.authToken = "yourTestAuthToken"
        val expectedResponse = 121342212

        val latch = CountDownLatch(1) // Initialize CountDownLatch with count 1

        var responseReceived: TutiResponse? = null
        var errorOccurred: Exception? = null

       // 2024-06-02 23:07:22.056 13114-13412 okhttp.OkHttpClient     sd.nil.app                           I  {"from_account":"0111493885","to_account":"12","amount":100.0,"signature":"","uuid":"1337a79b-3be8-4dc0-9ef9-d86ba99a2d35","signed_uuid":"YmmSJnW9Dvq4n2uda6rqIhsBy3TuzndWdod3m20ma0X3PW+RxQLj/FcYOxcJ4o2Z4Osug8KKO2siHkQRp/DdLIhnsXjS2i+nL8d3KmuxKVSzQ3T2WA6fLcZjEwlYMhUR3T+hCyNA9TU5eCHuvYJ7sMcE96AYPTrP9greQ0itaOERIRtU+twi3wo9Lo0ZlKsyuzPNlE+G4NTJb7R7cVOyegiSIZWrN/Xfi46HVFqjOlxTua8/mDE82Zt76u+W2B8z8Ivkv+70w+9bM4o04c1qdubfjATYGcgYLvUw8nwKKITHm2HkSMPLCyY3reqcCzj2NckkIyUaIRYKdD6H3EB5xQ==","timestamp":"2024-06-02T20:07:22.040Z"}

        // {"from_account":"0111493888","to_account":"12","amount":1000.0,"signature":"","uuid":"019c55a1-a748-4faf-88f4-2948c2ddd37c","signed_uuid":"Lr+zgWoSF/yeg00RgHIiAvcYU008F0GzCc72bdURy6CjtddBU/ucEwzOvf2jxV1MIwjWSedXII0cG8pRgFsTE3TUG6s8qjjkxVkPYQrbrJjjaWrRau/eBg05DVkcPXXABQK70Uz1ElsDyKwriJPS7I0YB4rEeTQktbERb4WXTKQFJ929kM8KCFkZslAYMDcCyz279JFrXQN4F47ipHONKxH92RfQovE5ZxJdmat/rtNb/Cap4CZObpIgnhqHNtFprkDEMAvJUoy5N+3MhgpPvdCZ3MBenPZxia9mFHxpr6P5Nhgr2hdqYJTDLHhkBFbrVD1hK+1URyHRmziMHAhe2g==","timestamp":"2024-06-06T07:43:25.118Z"}
        // Call the method
        tutiApiClient.noebsTransfer(NoebsTransfer("0111493885", toAccount = "249_ACCT_1", amount = 32.32, signature = "", uuid = "019c55a1-a748-4faf-88f4-2948c2ddd37c", signedUUid = "Lr+zgWoSF/yeg00RgHIiAvcYU008F0GzCc72bdURy6CjtddBU/ucEwzOvf2jxV1MIwjWSedXII0cG8pRgFsTE3TUG6s8qjjkxVkPYQrbrJjjaWrRau/eBg05DVkcPXXABQK70Uz1ElsDyKwriJPS7I0YB4rEeTQktbERb4WXTKQFJ929kM8KCFkZslAYMDcCyz279JFrXQN4F47ipHONKxH92RfQovE5ZxJdmat/rtNb/Cap4CZObpIgnhqHNtFprkDEMAvJUoy5N+3MhgpPvdCZ3MBenPZxia9mFHxpr6P5Nhgr2hdqYJTDLHhkBFbrVD1hK+1URyHRmziMHAhe2g=="), { response ->
            responseReceived = response
            latch.countDown() // Decrease the count of the latch, releasing the wait in test
        }, { _, error ->
            errorOccurred = error
            latch.countDown() // Ensure to count down in case of error too
        })

        // Wait for the operation to complete or timeout
        val callCompleted = latch.await(10, TimeUnit.SECONDS)

        // Assertions
        if (callCompleted) {
            assertEquals("ok", responseReceived!!.status)
            assertNull(errorOccurred)
        } else {
            fail("The call to inquireNoebsWallet did not complete within the expected time.")
        }
    }

    @Test
    fun retrieveLedgerTransactions() {
        val tutiApiClient = TutiApiClient(serverURL = "https://blue-violet-2528-icy-frog-2586.fly.dev/")
        tutiApiClient.authToken = "yourTestAuthToken"
        val expectedResponse = 121342212

        val latch = CountDownLatch(1) // Initialize CountDownLatch with count 1

        var responseReceived: List<Ledger>?= null
        var errorOccurred: Exception? = null

        // Call the method
        tutiApiClient.retrieveLedgerTransactions("249_ACCT_1", { response ->
            responseReceived = response
            latch.countDown() // Decrease the count of the latch, releasing the wait in test
        }, { _, error ->
            errorOccurred = error
            latch.countDown() // Ensure to count down in case of error too
        })

        // Wait for the operation to complete or timeout
        val callCompleted = latch.await(10, TimeUnit.SECONDS)

        // Assertions
        if (callCompleted) {
            assertEquals(expectedResponse, responseReceived)
            assertNull(errorOccurred)
        } else {
            fail("The call to inquireNoebsWallet did not complete within the expected time.")
        }
    }

    @Test
    fun retrieveNoebsTransactions() {
        val tutiApiClient = TutiApiClient(serverURL = "https://blue-violet-2528-icy-frog-2586.fly.dev/")
        tutiApiClient.authToken = "yourTestAuthToken"
        val expectedResponse = 121342212

        val latch = CountDownLatch(1) // Initialize CountDownLatch with count 1

        var responseReceived: List<NoebsTransaction>?= null
        var errorOccurred: Exception? = null

        // Call the method
        tutiApiClient.retrieveNoebsTransactions("249_ACCT_1", { response ->
            responseReceived = response
            latch.countDown() // Decrease the count of the latch, releasing the wait in test
        }, { _, error ->
            errorOccurred = error
            latch.countDown() // Ensure to count down in case of error too
        })

        // Wait for the operation to complete or timeout
        val callCompleted = latch.await(10, TimeUnit.SECONDS)

        // Assertions
        if (callCompleted) {
            assertEquals(expectedResponse, responseReceived)
            assertNull(errorOccurred)
        } else {
            fail("The call to inquireNoebsWallet did not complete within the expected time.")
        }
    }

    @Test
    fun setNoebsKYC() {
        val tutiApiClient = TutiApiClient(serverURL = "https://blue-violet-2528-icy-frog-2586.fly.dev/")
        tutiApiClient.authToken = "yourTestAuthToken"
        val expectedResponse = """{"code":"bad_request","message":"record not found"}"""

        val latch = CountDownLatch(1) // Initialize CountDownLatch with count 1

        var responseReceived: TutiResponse?= null
        var errorOccurred: Exception? = null
        var err: TutiResponse? = null

       val kyc = KYC(
            birthDate = Date(),
            issueDate = Date(),
            expirationDate = Date(),
            nationalNumber = "123456789",
            passportNumber = "987654321",
            gender = KYC.Gender.Male, // Use KYC.Gender.Female for female
            nationality = "US",
            holderName = "John Doe",
            selfie = "selfie.jpg",
            mobile = "1234567890",
            passportImage = "passport.jpg"
        )

        // Call the method
        tutiApiClient.setNoebsKYC(kyc, { response ->

            responseReceived = response
            latch.countDown() // Decrease the count of the latch, releasing the wait in test
        }, { errRes, error ->
            errorOccurred = error
            err = errRes
            latch.countDown() // Ensure to count down in case of error too
        })

        // Wait for the operation to complete or timeout
        val callCompleted = latch.await(10, TimeUnit.SECONDS)

        // Assertions
        if (callCompleted) {

            assertEquals("record not found", err!!.message)

//            assertEquals(expectedResponse, responseReceived)
//            assertNull(errorOccurred)
        } else {
            fail("The call to inquireNoebsWallet did not complete within the expected time.")
        }
    }
}


