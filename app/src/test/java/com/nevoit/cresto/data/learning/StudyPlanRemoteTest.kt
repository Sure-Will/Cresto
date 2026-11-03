package com.nevoit.cresto.data.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyPlanRemoteTest {

    @Test
    fun buildsContentsUrlWithEncodedSegments() {
        assertEquals(
            "https://api.github.com/repos/owner/notes/contents/area/plan.md",
            StudyPlanRemote.contentsUrl(" owner/notes ", "/area/plan.md")
        )
        assertEquals(
            "https://api.github.com/repos/owner/notes/contents/%E5%AD%A6%E4%B9%A0/my%20plan.md",
            StudyPlanRemote.contentsUrl("owner/notes", "学习/my plan.md")
        )
    }

    @Test
    fun rejectsInvalidSources() {
        assertNull(StudyPlanRemote.contentsUrl("owner", "plan.md"))
        assertNull(StudyPlanRemote.contentsUrl("owner/notes/extra", "plan.md"))
        assertNull(StudyPlanRemote.contentsUrl("https://github.com/owner/notes", "plan.md"))
        assertNull(StudyPlanRemote.contentsUrl("owner/notes", ""))
        assertNull(StudyPlanRemote.contentsUrl("owner/notes", "a/../secret.md"))
        assertNull(StudyPlanRemote.contentsUrl("owner/notes", "a//b.md"))
    }

    @Test
    fun classifiesFailures() {
        assertEquals(StudyPlanSyncError.Unauthorized, StudyPlanRemote.classify(401, null))
        assertEquals(StudyPlanSyncError.Forbidden, StudyPlanRemote.classify(403, "4999"))
        assertEquals(StudyPlanSyncError.RateLimited, StudyPlanRemote.classify(403, "0"))
        assertEquals(StudyPlanSyncError.RateLimited, StudyPlanRemote.classify(429, null))
        assertEquals(StudyPlanSyncError.NotFound, StudyPlanRemote.classify(404, null))
        assertEquals(StudyPlanSyncError.Server, StudyPlanRemote.classify(502, null))
    }

    @Test
    fun blankTokenIsNotSent() {
        assertEquals(
            StudyPlanFetchResult.Failure(StudyPlanSyncError.NotConfigured),
            StudyPlanRemote.fetch("owner/notes", "plan.md", " ")
        )
        assertEquals(
            StudyPlanFetchResult.Failure(StudyPlanSyncError.InvalidSource),
            StudyPlanRemote.fetch("owner", "plan.md", "token")
        )
    }
}
