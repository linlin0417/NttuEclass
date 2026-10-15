package tw.edu.irika.nttueclass.domain.repository

interface IDataSyncCoordinator {
    suspend fun syncAllData(): Result<Unit>
    suspend fun clearAllData()
}
