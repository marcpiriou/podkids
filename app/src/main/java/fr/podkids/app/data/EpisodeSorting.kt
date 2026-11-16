package fr.podkids.app.data

import fr.podkids.app.data.db.EpisodeEntity
import fr.podkids.app.data.settings.EpisodeOrder

/**
 * Trie les épisodes par date de publication. Les épisodes sans date sont placés à la fin, dans
 * l'ordre du flux. La base renvoie les épisodes du plus récent au plus ancien.
 */
fun List<EpisodeEntity>.sortedByOrder(order: EpisodeOrder): List<EpisodeEntity> {
    val dated = filter { it.publishedAt != null }
    val undated = filter { it.publishedAt == null }
    val sorted = when (order) {
        // sortedBy / sortedByDescending sont stables : à date égale, l'ordre d'origine est conservé.
        EpisodeOrder.OLDEST_FIRST -> dated.sortedBy { it.publishedAt }
        EpisodeOrder.NEWEST_FIRST -> dated.sortedByDescending { it.publishedAt }
    }
    return sorted + undated
}
