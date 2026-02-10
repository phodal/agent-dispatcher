package com.github.phodal.acpmanager.ide

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Tests for at-mention notification functionality.
 */
class AtMentionNotificationTest : BasePlatformTestCase() {

    @Test
    fun testEditorContextDataClass() {
        val context = EditorContext(
            filePath = "/path/to/file.kt",
            startLine = 10,
            endLine = 20,
            selectedText = "some code"
        )

        assertEquals("/path/to/file.kt", context.filePath)
        assertEquals(10, context.startLine)
        assertEquals(20, context.endLine)
        assertEquals("some code", context.selectedText)
    }

    @Test
    fun testEditorContextWithoutSelection() {
        val context = EditorContext(
            filePath = "/path/to/file.kt"
        )

        assertEquals("/path/to/file.kt", context.filePath)
        assertNull(context.startLine)
        assertNull(context.endLine)
        assertNull(context.selectedText)
    }

    @Test
    fun testIdeNotificationAtMentioned() {
        val notification = IdeNotification.AtMentioned(
            filePath = "/path/to/file.kt",
            startLine = 5,
            endLine = 15
        )

        assertEquals("at_mentioned", notification.method)
        assertEquals("/path/to/file.kt", notification.filePath)
        assertEquals(5, notification.startLine)
        assertEquals(15, notification.endLine)
    }

    @Test
    fun testIdeNotificationAtMentionedWithoutSelection() {
        val notification = IdeNotification.AtMentioned(
            filePath = "/path/to/file.kt"
        )

        assertEquals("at_mentioned", notification.method)
        assertEquals("/path/to/file.kt", notification.filePath)
        assertNull(notification.startLine)
        assertNull(notification.endLine)
    }

    @Test
    fun testIdeNotificationsListener() {
        val ideNotifications = IdeNotifications(project, kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
        ))

        var receivedNotification: IdeNotification? = null
        val listener = IdeNotifications.NotificationListener { notification ->
            receivedNotification = notification
        }

        ideNotifications.addListener(listener)

        val testNotification = IdeNotification.AtMentioned(
            filePath = "/test/file.kt",
            startLine = 1,
            endLine = 5
        )

        ideNotifications.broadcastNotification(testNotification)

        // Give coroutine time to execute
        Thread.sleep(100)

        assertNotNull(receivedNotification)
        assertEquals(testNotification.filePath, (receivedNotification as IdeNotification.AtMentioned).filePath)
    }

    @Test
    fun testIdeNotificationsRemoveListener() {
        val ideNotifications = IdeNotifications(project, kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
        ))

        var callCount = 0
        val listener = IdeNotifications.NotificationListener { _ ->
            callCount++
        }

        ideNotifications.addListener(listener)
        ideNotifications.removeListener(listener)

        val testNotification = IdeNotification.AtMentioned(
            filePath = "/test/file.kt"
        )

        ideNotifications.broadcastNotification(testNotification)

        // Give coroutine time to execute
        Thread.sleep(100)

        assertEquals(0, callCount)
    }

    @Test
    fun testAtMentionDetectionSimple() {
        val text1 = "Hey @agent, can you help?"
        val text2 = "Please review this code"
        val text3 = "@agent fix this bug"

        assertTrue(text1.contains("@"))
        assertFalse(text2.contains("@"))
        assertTrue(text3.contains("@"))
    }
}

