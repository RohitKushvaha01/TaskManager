package com.rk.taskmanager.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.with
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.screens.Filter
import com.rk.taskmanager.screens.ProcessItem
import com.rk.taskmanager.screens.Sort
import com.rk.taskmanager.screens.showFilter
import com.rk.taskmanager.screens.showSort

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ProcessSearchBar(
    modifier: Modifier = Modifier,
    viewModel: ProcessViewModel,
    navController: NavController,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val animatedPadding by animateDpAsState(
        targetValue = if (expanded) 0.dp else 8.dp,
        label = "searchBarPadding"
    )
    Box(
        modifier.fillMaxWidth().semantics { isTraversalGroup = true }
    ) {
        var query by rememberSaveable { mutableStateOf("") }

        // Collect search results as state
        val searchResults by viewModel.searchResults.collectAsState()

        SearchBar(
            modifier = Modifier.padding(animatedPadding)
                .align(Alignment.TopCenter)
                .semantics { traversalIndex = 0f },
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { newQuery ->
                        query = newQuery
                        viewModel.search(query)
                    },
                    onSearch = {},
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    placeholder = { Text("Search") },
                    trailingIcon = {
                        var showMoreMenu by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                //showFilter.value = true
                                showMoreMenu = true
                            }) {
                                Icon(imageVector = Icons.Outlined.MoreVert, null)
                            }
                        }

                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = {
                            showMoreMenu = false
                        }) {
                            DropdownMenuItem(text = {
                                Text("Filters")
                            }, onClick = {
                                showMoreMenu = false
                                showFilter.value = true
                            }, leadingIcon = {
                                Icon(imageVector = Filter, null)
                            })

                            DropdownMenuItem(text = {
                                Text("Sort")
                            }, onClick = {
                                showMoreMenu = false
                                showSort.value = true
                            }, leadingIcon = {
                                Icon(imageVector = Sort, null)
                            })



                        }
                    },
                    leadingIcon = {
                        AnimatedContent(
                            targetState = expanded,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300)) + scaleIn(tween(300)) + rotateIn() with
                                        fadeOut(tween(300)) + scaleOut(tween(300)) + rotateOut()
                            }
                        ) { targetExpanded ->
                            IconButton(onClick = {
                                expanded = expanded.not()
                            }) {
                                if (targetExpanded) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = "Search"
                                    )
                                }
                            }
                        }
                    }
                )
            },
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            // Use LazyColumn for virtual scrolling - only renders visible items
            LazyColumn {
                items(
                    items = searchResults,
                    key = { it.proc.pid }  // Use PID as stable key
                ) { proc ->
                    ProcessItem(
                        modifier = Modifier,
                        uiProc = proc,
                        navController = navController,
                        viewModel
                    )
                }
            }
        }
    }
}

@ExperimentalAnimationApi
fun rotateIn() = EnterTransition.None
@ExperimentalAnimationApi
fun rotateOut() = ExitTransition.None