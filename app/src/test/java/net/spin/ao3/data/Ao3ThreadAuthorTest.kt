package net.spin.ao3.data

import net.spin.ao3.util.usernameFromAuthorUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ao3ThreadAuthorTest {

    // ---- Comment thread depth ------------------------------------------------

    private val threadHtml = """
        <div id="comments_placeholder">
        <ol class="thread">
          <li class="odd guest comment group" id="comment_100" role="article">
            <h4 class="heading byline">
              <span>Reader One</span><span class="role"> (Guest)</span>
              <span class="parent">on <a href="/works/1/chapters/2">Chapter 1</a></span>
            </h4>
            <blockquote class="userstuff"><p>Root comment</p></blockquote>
            <ul class="actions"><li><a href="/comments/100">Reply</a></li></ul>
            <ol class="thread">
              <li class="even comment group user-5" id="comment_101" role="article">
                <h4 class="heading byline"><a rel="author" href="/users/AuthorName">AuthorName</a></h4>
                <blockquote class="userstuff"><p>First reply</p></blockquote>
              </li>
              <li class="odd comment group user-5" id="comment_102" role="article">
                <h4 class="heading byline"><a rel="author" href="/users/AuthorName">AuthorName</a></h4>
                <blockquote class="userstuff"><p>Second reply</p></blockquote>
              </li>
            </ol>
          </li>
          <li class="even comment group user-9" id="comment_103" role="article">
            <h4 class="heading byline"><a rel="author" href="/users/Other">Other</a></h4>
            <blockquote class="userstuff"><p>Second root</p></blockquote>
          </li>
        </ol>
        </div>
    """.trimIndent()

    @Test
    fun `replies inside nested ol thread keep their depth`() {
        val comments = Ao3Parser.parseComments(threadHtml)
        assertEquals(4, comments.size)

        val root = comments[0]
        assertEquals("Reader One", root.author)
        assertEquals(0, root.depth)
        assertNull(root.authorUrl) // guest

        val reply1 = comments[1]
        assertEquals("AuthorName", reply1.author)
        assertEquals(1, reply1.depth)
        assertEquals("/users/AuthorName", reply1.authorUrl)

        val reply2 = comments[2]
        assertEquals(1, reply2.depth)

        val root2 = comments[3]
        assertEquals("Other", root2.author)
        assertEquals(0, root2.depth)
    }

    @Test
    fun `full page html (new template) is parsed directly`() {
        // AO3 now answers the comments endpoint with a full HTML page sometimes.
        val page = "<html><body><div id=\"comments_placeholder\">$threadHtml</div></body></html>"
        val comments = Ao3Parser.parseComments(page)
        assertEquals(4, comments.size)
    }

    // ---- Real AO3 thread structure (sibling wrapper, verified live) ---------

    /**
     * AO3's current JS template emits each reply level as a BARE <li> sibling
     * (no comment class) that wraps its own <ol class="thread">. A naive
     * `ol.thread > li.comment.group` scan would also match the nested threads
     * and duplicate replies. The parser must only descend one level at a time.
     */
    private val siblingWrapperHtml = """
        <ol class="thread">
          <li class="odd comment group user-1" id="comment_200" role="article">
            <h4 class="heading byline"><a href="/users/Root">Root</a></h4>
            <blockquote class="userstuff"><p>Root comment</p></blockquote>
          </li>
          <li>
            <ol class="thread">
              <li class="even comment group user-2" id="comment_201" role="article">
                <h4 class="heading byline"><a href="/users/Reply">Reply</a></h4>
                <blockquote class="userstuff"><p>First reply</p></blockquote>
              </li>
              <li>
                <ol class="thread">
                  <li class="odd comment group user-3" id="comment_202" role="article">
                    <h4 class="heading byline"><a href="/users/Deep">Deep</a></h4>
                    <blockquote class="userstuff"><p>Reply to reply</p></blockquote>
                  </li>
                </ol>
              </li>
            </ol>
          </li>
          <li class="even comment group user-4" id="comment_203" role="article">
            <h4 class="heading byline"><a href="/users/Second">Second</a></h4>
            <blockquote class="userstuff"><p>Second root</p></blockquote>
          </li>
        </ol>
    """.trimIndent()

    @Test
    fun `sibling wrapper threads produce nested depths without duplicates`() {
        val comments = Ao3Parser.parseComments(siblingWrapperHtml)
        assertEquals(4, comments.size)
        assertEquals("Root", comments[0].author)
        assertEquals(0, comments[0].depth)
        assertEquals("Reply", comments[1].author)
        assertEquals(1, comments[1].depth)
        assertEquals("Deep", comments[2].author)
        assertEquals(2, comments[2].depth)
        assertEquals("Second", comments[3].author)
        assertEquals(0, comments[3].depth)
    }

    @Test
    fun `apostrophes in js-escaped html are unescaped once`() {
        // The JS string literal escapes ' as \' and " as \"; both must become
        // plain punctuation in the parsed text.
        // (the real payload is $j("#comments_placeholder").append("..."); — the
        // parser only needs the placeholder id and the append to locate the HTML)
        val js = """(function() { ${'$'}j("#comments_placeholder").append("<ol class=\"thread\"><li class=\"comment group\" id=\"comment_1\"><h4 class=\"heading byline\"><a href=\"/users/An\">An</a></h4><blockquote class=\"userstuff\"><p>Don\'t stop — it\'s fine.</p></blockquote></li></ol>"); })();""""""
        val comments = Ao3Parser.parseComments(js)
        assertEquals(1, comments.size)
        assertTrue("got: ${comments[0].text}", comments[0].text.contains("Don't"))
        assertTrue("backslash survived: ${comments[0].text}", !comments[0].text.contains("\\'"))
    }

    // ---- Author profile ------------------------------------------------------

    private val profileHtml = """
        <h2 class="heading">Vichan</h2>
        <dl class="meta">
          <dt class="pseuds">My pseuds:</dt>
          <dd class="pseuds"><a href="/users/Vichan/pseuds/Vichan">Vichan</a> and <a href="/users/Vichan/pseuds/k-vichan">k-vichan</a></dd>
          <dt>I joined on:</dt><dd>2010-01-12</dd>
        </dl>
        <div id="bio" class="userstuff"><p>I write angsty HP fic.</p></div>
    """.trimIndent()

    @Test
    fun `profile parsing extracts name pseuds join date and bio`() {
        val p = Ao3Parser.parseAuthorProfile(profileHtml, "Vichan")
        assertEquals("Vichan", p.displayName)
        assertEquals("2010-01-12", p.joined)
        assertEquals(listOf("Vichan", "k-vichan"), p.pseuds)
        assertTrue(p.bio.contains("angsty"))
    }

    private val worksHtml = """
        <h2 class="heading">11 Works by Vichan</h2>
        <ol class="work index group">
          <li id="work_28534965" class="work blurb group work-28534965 user-4758" role="article">
            <div class="header module">
              <h4 class="heading">
                <a href="/works/28534965">Redivider</a>
                by <a rel="author" href="/users/Vichan/pseuds/Vichan">Vichan</a>
              </h4>
              <h5 class="fandoms heading"><a class="tag" href="/tags/Harry%20Potter%20-%20J*d*%20K*d*%20Rowling/works">Harry Potter - J. K. Rowling</a></h5>
              <ul class="required-tags"><li><span class="rating" title="Mature">Mature</span></li></ul>
            </div>
            <div class="summary module"><blockquote class="userstuff summary"><p>Redivider summary</p></blockquote></div>
            <dl class="stats">
              <dt>Words:</dt><dd class="words">107,493</dd>
              <dt>Chapters:</dt><dd class="chapters">17/?</dd>
              <dt>Comments:</dt><dd class="comments">42</dd>
              <dt>Kudos:</dt><dd class="kudos">1,234</dd>
              <dt>Hits:</dt><dd class="hits">9,876</dd>
            </dl>
          </li>
        </ol>
    """.trimIndent()

    @Test
    fun `author works page parses count and blurbs`() {
        val (count, works) = Ao3Parser.parseAuthorWorks(worksHtml)
        assertEquals(11, count)
        assertEquals(1, works.size)
        val w = works[0]
        assertEquals(28534965L, w.id)
        assertEquals("Redivider", w.title)
        assertEquals("Vichan", w.author)
        assertEquals("Mature", w.rating)
        assertEquals(107493L, w.words)
        assertEquals(17, w.chapterCount)
        assertNull(w.chapterTotal)
    }

    // ---- usernameFromAuthorUrl ----------------------------------------------

    @Test
    fun `username is extracted from various author hrefs`() {
        assertEquals("Vichan", usernameFromAuthorUrl("/users/Vichan"))
        assertEquals("Vichan", usernameFromAuthorUrl("/users/Vichan/pseuds/k-vichan"))
        assertEquals("Some-Name", usernameFromAuthorUrl("https://archiveofourown.org/users/Some-Name"))
        assertNull(usernameFromAuthorUrl(null))
        assertNull(usernameFromAuthorUrl(""))
    }
}
