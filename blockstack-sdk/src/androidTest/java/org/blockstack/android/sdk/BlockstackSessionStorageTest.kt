package org.blockstack.android.sdk

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import kotlinx.coroutines.runBlocking
import org.blockstack.android.sdk.model.*
import org.blockstack.android.sdk.test.TestActivity
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


private val PRIVATE_KEY = "a5c61c6ca7b3e7e55edee68566aeab22e4da26baa285c7bd10e8d2218aa3b229"
private val PUBLIC_KEY = "027d28f9951ce46538951e3697c62588a87f1f1f295de4a14fdd4c780fc52cfe69"
private val DECENTRALIZED_ID = "did:btc-addr:1NZNxhoxobqwsNvTb16pdeiqvFvce3Yg8U"
private val BITCOIN_ADDRESS = "1NZNxhoxobqwsNvTb16pdeiqvFvce3Yg8U"


@RunWith(AndroidJUnit4::class)
class BlockstackSessionStorageTest {
    private lateinit var blockstack: Blockstack
    @get:Rule
    val rule = ActivityTestRule(TestActivity::class.java)

    private lateinit var session: BlockstackSession

    @Before
    fun setup() {
        blockstack = Blockstack()
        session = BlockstackSession(
                appConfig = "https://flamboyant-darwin-d11c17.netlify.com".toBlockstackConfig(emptyArray()),
                sessionStore = sessionStoreforIntegrationTests(rule),
                blockstack = blockstack)
    }


    @Test
    fun testPutStringFileTwice() {
        var result1: String? = null
        var result2: String? = null
        val latch = CountDownLatch(2)
        runBlocking {

            if (session.isUserSignedIn()) {
                session.putFile("try.txt", "Hello Test", PutFileOptions(false)) {
                    if (it.value is String) {
                        result1 = it.value as String
                    }
                    latch.countDown()
                }
                session.putFile("try.txt", "Hello Test2", PutFileOptions(false)) {
                    if (it.value != null) {
                        result2 = it.value as String
                    }
                    latch.countDown()
                }
            } else {
                latch.countDown()
                latch.countDown()
            }
        }
        latch.await()
        assertThat(result1, `is`(notNullValue()))
        assertThat(result2, `is`(notNullValue()))
    }

    @Test
    fun testEncryptDecryptString() {
        assertTrue(session.isUserSignedIn())
        val options = CryptoOptions()
        val plainContent = "hello from test"
        val encResult = blockstack.encryptContent(plainContent, options = options)
        assertTrue(encResult.hasValue)
        val decResult = blockstack.decryptContent(encResult.value!!.json.toString(), false, options)
        assertThat(decResult.value as String, `is`(plainContent))
    }

    @Test
    fun testEncryptDecryptBinary() {
        assertTrue(session.isUserSignedIn())
        val binaryContent = getImageBytes()
        val options = CryptoOptions()
        val encResult = blockstack.encryptContent(binaryContent, options = options)
        assertTrue(encResult.hasValue)
        val decResult = blockstack.decryptContent(encResult.value!!.json.toString(), true, options)
        assertThat((decResult.value as ByteArray).size, `is`(binaryContent.size))
    }

    @Test
    fun testGetFileFor404File() {
        var result: Result<Any>? = null
        val latch = CountDownLatch(1)

        runBlocking {
            if (session.isUserSignedIn()) {
                session.getFile("404file.txt", GetFileOptions(false)) {
                    result = it
                    latch.countDown()
                }
            } else {
                latch.countDown()
            }
        }
        latch.await()
        assertThat(result, `is`(notNullValue()))
        assertThat(result?.value, `is`(nullValue()))
        assertThat(result?.error, `is`(nullValue()))
    }

    @Test
    fun testPutGetStringFile() {
        var result: String? = null
        val latch = CountDownLatch(1)

        runBlocking {
            if (session.isUserSignedIn()) {
                session.putFile("try.txt", "Hello Test", PutFileOptions(false)) {
                    runBlocking {
                        session.getFile("try.txt", GetFileOptions(false)) {
                            if (it.value is String) {
                                result = it.value as String
                            }
                            latch.countDown()
                        }
                    }
                }
            } else {
                latch.countDown()
            }
        }
        latch.await()
        assertThat(result, `is`("Hello Test"))
    }

    @Test
    fun testPutGetStringFileWithContentType() {
        var result: Any? = null
        val latch = CountDownLatch(1)

        runBlocking {

            if (session.isUserSignedIn()) {
                session.putFile("try.txt", "Hello Test", PutFileOptions(false, "application/x.foo")) {
                    val u = URL(it.value).openConnection()
                    u.connect()
                    result = u.contentType
                    latch.countDown()
                }
            } else {
                latch.countDown()
            }
        }
        latch.await()
        assertThat(result as String, `is`("application/x.foo"))
    }

    @Test
    fun testPutGetEncryptedStringFile() {
        var result: String? = null
        val latch = CountDownLatch(1)

        runBlocking {

            if (session.isUserSignedIn()) {
                session.putFile("try.txt", "Hello Test", PutFileOptions(true)) {
                    runBlocking {

                        session.getFile("try.txt", GetFileOptions(true)) {
                            if (it.value is String) {
                                result = it.value as String
                            }
                            latch.countDown()
                        }
                    }
                }
            } else {
                latch.countDown()
            }
        }
        latch.await()
        assertThat(result, `is`("Hello Test"))
    }

    @Test
    fun testPutGetBinaryFile() {
        val bitMapData = getImageBytes()

        var result: ByteArray? = null

        val latch = CountDownLatch(1)

        runBlocking {

            if (session.isUserSignedIn()) {
                session.putFile("try.txt", bitMapData, PutFileOptions(false)) {
                    runBlocking {

                        session.getFile("try.txt", GetFileOptions(false)) {
                            if (it.value is ByteArray) {
                                result = it.value as ByteArray
                            }
                            latch.countDown()
                        }
                    }
                }
            } else {
                latch.countDown()
            }
        }
        latch.await()
        assertThat(result?.size, `is`(bitMapData.size))
    }

    @Test
    fun testPutGetEncryptedBinaryFile() {
        val bitMapData = getImageBytes()

        var result: ByteArray? = null

        val latch = CountDownLatch(1)

        runBlocking {

            if (session.isUserSignedIn()) {
                session.putFile("try.txt", bitMapData, PutFileOptions(true)) {
                    runBlocking {

                        session.getFile("try.txt", GetFileOptions(true)) {
                            if (it.value is ByteArray) {
                                result = it.value as ByteArray
                            }
                            latch.countDown()
                        }
                    }
                }
            } else {
                latch.countDown()
            }
        }
        latch.await()
        assertThat(result?.size, `is`(bitMapData.size))
    }

    @Test
    fun testPutGetFileSigned() {
        var result: String? = null
        val latch = CountDownLatch(1)
        runBlocking {

            if (session.isUserSignedIn()) {
                session.putFile("try.txt", "Hello Test", PutFileOptions(false, sign = true)) {
                    runBlocking {

                        session.getFile("try.txt", GetFileOptions(false, verify = true)) {
                            if (!it.hasErrors) {
                                Log.d("blockstack test", it.value as String)
                                result = it.value as String
                            }
                            latch.countDown()
                        }
                    }
                }
            } else {
                latch.countDown()
            }
        }
        latch.await()
        assertThat(result, `is`("Hello Test"))
    }

    @Test
    fun testPutGetFileMissingSignature() {
        var result: String? = null
        val latch = CountDownLatch(1)

        runBlocking {
            if (session.isUserSignedIn()) {
                session.putFile("try.txt", "Hello Test", PutFileOptions(false, sign = true)) {
                    runBlocking {
                        session.deleteFile("try.txt.sig", DeleteFileOptions()) {
                            if (!it.hasErrors) {
                                runBlocking {
                                    session.getFile("try.txt", GetFileOptions(false, verify = true)) {

                                        if (!it.hasErrors) {
                                            result = it.value as String
                                        } else {
                                            result = it.error?.message
                                        }
                                        latch.countDown()
                                    }
                                }
                            } else {
                                result = "error while deleting signature"
                                latch.countDown()
                            }
                        }
                    }

                }
            } else {
                latch.countDown()
            }
        }
        latch.await()
        assertThat(result, startsWith("SignatureVerificationError: Failed to verify signature: Failed to obtain signature for file: try.txt"))
    }

    @Test
    fun testPutGetFileSignedEncrypted() {
        var result: JSONObject? = null
        val latch = CountDownLatch(1)

        runBlocking {

            if (session.isUserSignedIn()) {
                session.putFile("try.txt", "Hello Test", PutFileOptions(true, sign = true)) {
                    runBlocking {
                        session.getFile("try.txt", GetFileOptions(false)) {
                            if (!it.hasErrors) {
                                result = JSONObject(it.value as String)
                            }
                            latch.countDown()
                        }
                    }
                }
            } else {
                latch.countDown()
            }
        }
        latch.await()
        assertThat(result, `is`(notNullValue()))
        assertThat(result!!.getString("signature"), `is`(notNullValue()))
    }

    @Test
    fun testPutGetFileInvalidSigned() {
        var result: String? = null
        val latch = CountDownLatch(1)

        val invalidSignedEncryptedText = "{\"signature\":\"INVALID_SIGNATURE\",\"publicKey\":\"024634ee1d4ff57f2e0ec7a847e1705ec562949f84a83d1f5fdb5956220a9775e0\",\"cipherText\":\"{\\\"iv\\\":\\\"329acaeffe36e8ae58365b56b31af640\\\",\\\"ephemeralPK\\\":\\\"0333fde58c40196efa696dde93fb615e8e960bf52d78ab883d67fb56d4b9a10c5a\\\",\\\"cipherText\\\":\\\"143df68fd1542b29febe2b0843e723af\\\",\\\"mac\\\":\\\"68c3e439c26a2be400aeb278ed7061a8802b0366bf79a1d64a7a6e10e4710047\\\",\\\"wasString\\\":true}\"}"
        if (session.isUserSignedIn()) {
            runBlocking {
                session.putFile("try.txt", invalidSignedEncryptedText, PutFileOptions(false)) {
                    runBlocking {
                        session.getFile("try.txt", GetFileOptions(true, verify = true)) {
                            if (it.hasErrors) {
                                result = it.error?.message
                            }
                            latch.countDown()
                        }
                    }
                }
            }
        } else {
            latch.countDown()
        }
        latch.await()
        assertThat(result, `is`("Error: Signature without r or s"))
    }


    @Test
    fun getUserAppFileUrlReturns_NO_URL_forNonPublicFile() {
        val url = blockstack.getUserAppFileUrl("non_public_file.txt", "friedger.id", "https://blockstack-todos.appartisan.com/", null)
        assertThat(url, `is`("NO_URL"))
    }

    @Test
    fun getAppBucketUrlReturnsUrl() {
        val url = runBlocking {
            blockstack.getAppBucketUrl("https://hub.blockstack.org", PRIVATE_KEY)
        }

        assertThat(url, `is`("https://gaia.blockstack.org/hub/1NZNxhoxobqwsNvTb16pdeiqvFvce3Yg8U/"))
    }

    @Test
    fun listFilesReturnsCorrectNumberOfFiles() {
        var count = 0
        val countResult = runBlocking {
            session.listFiles {
                count++
                true
            }
        }
        assertThat(countResult.value, `is`(count))
    }

    @Test
    fun listFilesCanHandleErrorInCallback() {
        val countResult = runBlocking {
            session.listFiles { _ ->
                throw RuntimeException("I want to make the API crash!")
            }
        }
        assertThat(countResult.error?.message, `is`("I want to make the API crash!"))
    }

    @Test
    fun testGetFileUrlForEncryptedFile() {
        val latch = CountDownLatch(1)
        var urlResult: Result<String>? = null
        runBlocking {
            if (session.isUserSignedIn()) {
                session.putFile("try.txt", "Hello Test", PutFileOptions(true)) {
                    runBlocking {
                        urlResult = session.getFileUrl("try.txt", GetFileOptions(true))
                    }
                }
            } else {
                latch.countDown()
            }
        }
        latch.await(1, TimeUnit.MINUTES)
        assertThat(urlResult?.value, notNullValue())
        assertThat(URL(urlResult!!.value).readText(), startsWith("{\"iv\":"))
    }

    @Test
    fun testGetFileUrlForUnencryptedFile() {
        val latch = CountDownLatch(1)
        var urlResult: Result<String>? = null
        runBlocking {
            if (session.isUserSignedIn()) {
                session.putFile("try.txt", "Hello Test", PutFileOptions(false)) {
                    urlResult = runBlocking {
                        session.getFileUrl("try.txt", GetFileOptions(false))
                    }
                }
            } else {
                latch.countDown()
            }
        }
        latch.await(1, TimeUnit.MINUTES)
        assertThat(urlResult?.value, notNullValue())
        assertThat(URL(urlResult!!.value).readText(), `is`("Hello Test"))
    }

    @Test
    fun testGetFileUrlFor404File() {
        val latch = CountDownLatch(1)
        val urlResult = runBlocking {
            session.getFileUrl("404file.txt", GetFileOptions(false))
        }
        assertThat(urlResult.value, notNullValue())
        try {
            URL(urlResult.value).readText()
            fail("Should throw FileNotFoundException")
        } catch (e: FileNotFoundException) {
            // success
        }
    }

    @Test
    fun testDeleteFile() {
        var result: Result<Any>? = null
        val latch = CountDownLatch(1)

        runBlocking {
            if (session.isUserSignedIn()) {
                session.putFile("try.txt", "Hello Test", PutFileOptions(false)) {
                    runBlocking {
                        session.deleteFile("try.txt") {
                            runBlocking {
                                session.getFile("try.txt", GetFileOptions(false)) {
                                    result = it
                                    latch.countDown()
                                }
                            }
                        }
                    }
                }
            } else {
                latch.countDown()
            }
        }
        latch.await()
        assertThat(result, `is`(notNullValue()))
        assertThat(result!!.value, `is`(nullValue()))
        assertThat(result!!.error, `is`(nullValue()))
    }

    private fun getImageBytes(): ByteArray {
        val drawable: BitmapDrawable = rule.activity.resources.getDrawable(org.blockstack.android.sdk.test.R.drawable.blockstackteam) as BitmapDrawable

        val bitmap = drawable.bitmap
        val stream = ByteArrayOutputStream()

        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val bitMapData = stream.toByteArray()
        return bitMapData
    }
}