package devicegroups

import mu.KLogging

object GroupManager : KLogging() {

    /**
     *  Maps groupId to groupData.
     *
     *  groupId: A static random string, generated within my app, which identifies the group.
     */
    private var groups = mutableMapOf<String, GroupData>()

    /**
     *  Get members for corresponding group id.
     *
     *  @param groupId groupId of interest.
     *
     *  @return corresponding members.
     */
    fun getGroupMembers(groupId: String): Set<String>? {
        return groups[groupId]?.members?.toSet()
    }

    /**
     * Register group.
     */
    fun registerGroup(groupId: String, groupMembers: MutableSet<String>, groupCreator: String? = null) {
        if (groupId in groups) {
            logger.error("Cannot register group with id $groupId")
            return
        }
        val existingMembers = groups.getOrDefault(groupId, null)?.members ?: mutableSetOf<String>()
        val newMembers = existingMembers.union(groupMembers).toMutableSet()
        if (groupCreator != null)
            newMembers.add(groupCreator)
        groups[groupId] = GroupData(newMembers)
    }

    fun resetState() {
        groups = mutableMapOf()
    }
}

/**
 *  A class that holds all data about a group (besides the groupId).
 *
 *  members: A set which contains the emails of all the users in the group.
 */
data class GroupData(val members: MutableSet<String>)