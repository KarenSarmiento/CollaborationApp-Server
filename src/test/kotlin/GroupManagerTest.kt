import devicegroups.GroupManager
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
        val groupMembers = mutableSetOf<String>("m1", "m2", "m3")
        GroupManager.registerGroup(groupId, groupMembers)

        // WHEN
        val membersActual = GroupManager.getGroupMembers(groupId)

        // THEN
        assertEquals(groupMembers.toSet(), membersActual)
    }

    @Test
    fun `groups that are registered twice are correctly updated`() {
        // GIVEN
        val groupId = "group-id"
        val groupMembers1 = mutableSetOf<String>("m1", "m2", "m3")
        GroupManager.registerGroup(groupId, groupMembers1)

        val groupMembers2 = mutableSetOf<String>("n1", "n2", "n3")
        GroupManager.registerGroup(groupId, groupMembers2)

        // WHEN
        val membersActual = GroupManager.getGroupMembers(groupId)

        // THEN
        assertEquals(groupMembers1.toSet(), membersActual)
    }
}