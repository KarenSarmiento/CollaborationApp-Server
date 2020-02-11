import devicegroups.GroupManager
import io.mockk.spyk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupManagerTest {

    @BeforeEach
    fun beforeEach() {
        GroupManager.resetState()
    }

    @Test
    fun `groups are correctly registered and can be queried thereafter for keys and members`() {
        // GIVEN
        val groupId = "group-id"
        val groupKey = "group-key"
        val groupMembers = mutableSetOf<String>("m1", "m2", "m3")
        GroupManager.registerGroup(groupId, groupKey, groupMembers)

        // WHEN
        val groupKeyActual = GroupManager.getGroupKey(groupId)
        val membersActual = GroupManager.getGroupMembers(groupId)

        // THEN
        assertEquals(groupKey, groupKeyActual)
        assertEquals(groupMembers.toSet(), membersActual)
    }

    @Test
    fun `groups that are registered twice are correctly updated`() {
        // GIVEN
        val groupId = "group-id"
        val groupKey1 = "group-key-1"
        val groupMembers1 = mutableSetOf<String>("m1", "m2", "m3")
        GroupManager.registerGroup(groupId, groupKey1, groupMembers1)

        val groupKey2 = "group-key-2"
        val groupMembers2 = mutableSetOf<String>("n1", "n2", "n3")
        GroupManager.registerGroup(groupId, groupKey2, groupMembers2)

        // WHEN
        val groupKeyActual = GroupManager.getGroupKey(groupId)
        val membersActual = GroupManager.getGroupMembers(groupId)

        // THEN
        assertEquals(groupKey1, groupKeyActual)
        assertEquals(groupMembers1.toSet(), membersActual)
    }
}