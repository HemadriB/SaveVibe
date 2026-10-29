package com.hemadri.statushub.ui.instagram

import androidx.compose.ui.res.stringResource
import com.hemadri.statushub.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hemadri.statushub.instagram.InstagramUser
import com.hemadri.statushub.ui.components.EmptyState
import com.hemadri.statushub.ui.theme.BrandGradients
import com.hemadri.statushub.ui.theme.SunsetPink
import com.hemadri.statushub.viewmodel.InstagramSearchViewModel

/**
 * Search Instagram accounts to view and download their stories, highlights and profile photo
 */
@Composable
fun InstagramSearchScreen(
    onBack: () -> Unit,
    onOpenProfile: (username: String, user: InstagramUser?) -> Unit,
    viewModel: InstagramSearchViewModel = viewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.ig_cd_back))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text(stringResource(R.string.ig_search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = SunsetPink) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ig_cd_clear))
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        // Exact username typed: open it directly
                        query.trim().removePrefix("@").takeIf { it.isNotEmpty() }?.let { typed ->
                            onOpenProfile(typed, results.firstOrNull { it.username.equals(typed, ignoreCase = true) })
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SunsetPink,
                        unfocusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                query.trim().length < 2 -> EmptyState(
                    icon = Icons.Rounded.Search,
                    brush = BrandGradients.instagram,
                    title = stringResource(R.string.ig_search_empty_title),
                    message = stringResource(R.string.ig_search_empty_message)
                )
                results.isEmpty() && !isSearching -> EmptyState(
                    icon = Icons.Rounded.Search,
                    brush = BrandGradients.instagram,
                    title = stringResource(R.string.ig_search_no_results_title),
                    message = error ?: stringResource(R.string.ig_search_no_results_message, query.trim().removePrefix("@"))
                )
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    if (isSearching) {
                        item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = SunsetPink) }
                    }
                    items(results, key = { it.id.ifEmpty { it.username } }) { user ->
                        ListItem(
                            leadingContent = { StoryAvatar(user.profilePicUrl, size = 52.dp, showRing = false) },
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(user.username, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (user.isPrivate) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.ig_cd_private), modifier = Modifier.size(14.dp))
                                    }
                                }
                            },
                            supportingContent = user.fullName?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                            modifier = Modifier.clickable { onOpenProfile(user.username, user) }
                        )
                    }
                }
            }
        }
    }
}
