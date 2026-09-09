package tw.edu.irika.nttueclass.domain.model

import tw.edu.irika.nttueclass.domain.util.ClassroomHelper

data class Course(
    val id: String,
    val code: String,
    val name: String,
    val instructor: String,
    val classroom: String,
    val credits: Int,
    val semester: String,
    val unreadAnnouncementsCount: Int = 0,
    val pendingTasksCount: Int = 0
) {
    val classroomCode: String get() = ClassroomHelper.extractClassroomCode(classroom)
    val buildingCode: String get() = ClassroomHelper.extractBuildingCode(classroom)
}
