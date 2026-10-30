package com.statushub.instagram

import java.io.IOException

/**
 * A single photo or video inside an Instagram post / story
 */
data class InstagramMedia(
    val url: String,
    val isVideo: Boolean,
    val thumbnailUrl: String?
)

/**
 * An Instagram post, reel, carousel or story with all of its media
 */
data class InstagramPost(
    val code: String,
    val username: String?,
    val caption: String?,
    val media: List<InstagramMedia>
)

/**
 * An Instagram account (search results, story tray, profiles)
 */
data class InstagramUser(
    val id: String,
    val username: String,
    val fullName: String?,
    val profilePicUrl: String?,
    val isPrivate: Boolean = false
)

/**
 * One circle in the stories row
 * @param seen true when the user already watched all of this person's current stories
 */
data class StoryTrayItem(
    val user: InstagramUser,
    val seen: Boolean,
    val isOwn: Boolean
)

/**
 * A single story (or highlight) frame
 */
data class StoryItem(
    val id: String,
    val media: InstagramMedia,
    /** epoch seconds */
    val takenAt: Long
)

/**
 * A highlight album on a profile
 * @param id reel id in the form "highlight:<number>"
 */
data class Highlight(
    val id: String,
    val title: String,
    val coverUrl: String?
)

data class InstagramProfile(
    val user: InstagramUser,
    val hdProfilePicUrl: String?,
    val highlights: List<Highlight>
)

/**
 * @param loginRequired true when logging in to Instagram would likely fix the error
 */
class InstagramException(message: String, val loginRequired: Boolean = false) : IOException(message)
