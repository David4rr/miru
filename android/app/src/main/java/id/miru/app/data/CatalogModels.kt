package id.miru.app.data

data class CatalogSortOption(
    val label: String,
    val value: String
)

data class CatalogFilterOptions(
    val genres: List<String> = emptyList(),
    val seasons: List<String> = emptyList(),
    val formats: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    val sortOptions: List<CatalogSortOption> = emptyList()
)

data class CatalogBrowseParams(
    val genre: String? = null,
    val season: String? = null,
    val year: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val sort: String? = "POPULARITY_DESC",
    val page: Int = 1,
    val perPage: Int = 21
) {
    val hasActiveFilters: Boolean
        get() = !genre.isNullOrBlank() ||
                !season.isNullOrBlank() ||
                year != null ||
                !format.isNullOrBlank() ||
                !status.isNullOrBlank() ||
                (sort != null && sort != "POPULARITY_DESC")
}

data class TerminalResult(
    val success: Boolean,
    val command: String,
    val output: String
)
