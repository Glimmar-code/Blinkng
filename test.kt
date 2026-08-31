import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
fun main() {
    val t = "application/json".toMediaType()
    "".toResponseBody(t)
}
