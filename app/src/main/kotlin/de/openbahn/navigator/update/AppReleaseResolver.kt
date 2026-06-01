package de.openbahn.navigator.update

/**
 * Resolves the newest installable APK for this app.
 * Prefers the F-Droid custom repo (where CI publishes) and falls back to GitHub Releases.
 */
class AppReleaseResolver(
    private val fdroidRepoClient: FdroidRepoClient = FdroidRepoClient(),
    private val gitHubReleaseClient: GitHubReleaseClient = GitHubReleaseClient(),
) {
    suspend fun fetchLatestApk(packageName: String): ReleaseApk? {
        val fromFdroid = fdroidRepoClient.fetchLatestApk(packageName)
        val fromGitHub = gitHubReleaseClient.fetchLatestApk()
        return listOfNotNull(fromFdroid, fromGitHub).maxByOrNull { it.versionCode }
    }
}
