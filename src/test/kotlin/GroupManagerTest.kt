import devicegroups.GroupManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import javax.json.Json


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupManagerTest {
    @Test
    fun `given an arbitrary firebase packet, an ack is sent with the correct params`() {
        val ja = Json.createArrayBuilder()
            .add("cyU80XGOucc:APA91bHNnnPcFUz3taCwRA-hAxcRZpqs5fLEdbA6604PdWcFQsB-WadbdYHYL26gb2Kys97MwkC7uiihqPJBhlkRPvRt1Bf8M5L-yJG1h-sGVnUfDA_5ugxr-NOkW6JhQ1hBCWhmafHf")
            .add("lol")
            .build()
        GroupManager.maybeCreateGroup("another-test11", ja)
    }
}