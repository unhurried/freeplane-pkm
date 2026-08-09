/**
 * One full-text search result: the matched file, its path relative to the
 * document directory (as stored in the index, forward-slash separated even on
 * Windows), and a short content snippet around the match. The snippet is
 * empty when the file has no extracted content - e.g. an unsupported format
 * that only matched by filename.
 */
class SearchHit {
    File file
    String relativePath
    String snippet
}
