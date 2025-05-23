package dev.bnorm.storyboard.easel

import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.createChildTransition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import dev.bnorm.storyboard.AdvanceDirection
import dev.bnorm.storyboard.Frame
import dev.bnorm.storyboard.Scene
import dev.bnorm.storyboard.Storyboard
import kotlin.math.abs
import kotlin.properties.Delegates

@RequiresOptIn
annotation class ExperimentalStoryStateApi

@OptIn(ExperimentalStoryStateApi::class)
@Composable
fun rememberStoryState(
    storyboard: Storyboard,
    initialIndex: Storyboard.Index = Storyboard.Index(0, 0),
): StoryState {
    val state = rememberStoryState(initialIndex)
    state.updateStoryboard(storyboard)
    return state
}

@Composable
@ExperimentalStoryStateApi
fun rememberStoryState(
    initialIndex: Storyboard.Index = Storyboard.Index(0, 0),
): StoryState {
    return rememberSaveable { StoryState(initialIndex) }
}

@Stable
class StoryState @ExperimentalStoryStateApi constructor(
    initialIndex: Storyboard.Index = Storyboard.Index(0, 0),
) {
    private var _storyboard: Storyboard? by mutableStateOf(null)
    val storyboard: Storyboard
        get() = _storyboard ?: error("Storyboard uninitialized.")

    private var frames: List<StateFrame<*>> by Delegates.notNull()
    private var byIndex: Map<Storyboard.Index, StateFrame<*>> by Delegates.notNull()

    // TODO the mutable state is a workaround for SeekableTransitionState not
    //  supporting `snapTo` without a transition.
    // TODO report a bug?
    // Defaults to 0, but will always be updated via updateStoryboard before first use.
    private var frameIndex by mutableStateOf(SeekableTransitionState(0))

    var currentIndex: Storyboard.Index by mutableStateOf(initialIndex)
        private set

    var targetIndex: Storyboard.Index by mutableStateOf(initialIndex)
        private set

    val storyDistance: Float
        get() {
            if (_storyboard == null) return 0f
            return frames.lastIndex.toFloat()
        }

    val storyProgress: Float
        get() {
            if (_storyboard == null) return 0f
            val current = frameIndex.currentState
            val target = frameIndex.targetState
            val fraction = when {
                current == target -> 0f
                target > current -> frameIndex.fraction
                else -> -frameIndex.fraction
            }

            return current.toFloat() + fraction
        }

    val advancementDistance: Float
        get() {
            if (currentIndex == targetIndex) return 1f
            val start = byIndex.getValue(currentIndex).frameIndex
            val target = byIndex.getValue(targetIndex).frameIndex
            return abs(target - start).toFloat()
        }

    val advancementProgress: Float
        get() {
            if (currentIndex == targetIndex) return 1f
            val start = byIndex.getValue(currentIndex).frameIndex
            val current = frameIndex.currentState
            val fraction = if (current == frameIndex.targetState) 0f else frameIndex.fraction
            return abs(current - start) + fraction
        }

    // TODO manage scope + job + launch internally? or have utilities for it?
    //  - seems like a lot of places need to manage a scope and job,
    //    there should be some utility for each of those place to advance/jump
    //    without suspension. This would also allow for coordinated cancellation.
    //  - is that this class? is it some other utility?
    //  - do we need to be careful about which scope the advancement runs in?
    suspend fun advance(direction: AdvanceDirection): Boolean {
        if (_storyboard == null) return false // Cannot advance without Storyboard.

        val currentDirection = toDirection(frameIndex.currentState, frameIndex.targetState)
        if (currentDirection == null || currentDirection == direction) {
            if (currentDirection == direction) {
                // Snap to the target and continue.
                val target = byIndex.getValue(targetIndex)
                snapTo(target.frameIndex)
                currentIndex = target.storyboardIndex
                // Fall-through and continue with the next advancement.
            }

            // Find the next target frame index.
            val targetFrameIndex = findTargetFrameIndex(direction) ?: return false
            targetIndex = frames[targetFrameIndex].storyboardIndex
        } else {
            // Reverse directions.
            val tmp = currentIndex
            currentIndex = targetIndex
            targetIndex = tmp

            frameIndex.animateTo(frameIndex.currentState)
        }

        // Animate to the target frame.
        var currentFrameIndex = frameIndex.currentState
        while (frames[currentFrameIndex].storyboardIndex != targetIndex) {
            currentFrameIndex += direction.toInt()
            frameIndex.animateTo(currentFrameIndex)
        }

        currentIndex = targetIndex
        return true
    }

    private fun findTargetFrameIndex(direction: AdvanceDirection): Int? {
        require(frameIndex.currentState == frameIndex.targetState) {
            "cannot find target state during transition: " +
                    "currentState=${frameIndex.currentState} " +
                    "targetState=${frameIndex.targetState}"
        }

        var targetState = frameIndex.currentState + direction.toInt()
        if (targetState !in frames.indices) return null

        while (frames[targetState].frame !is Frame.State) {
            targetState += direction.toInt()
        }
        return targetState
    }

    suspend fun jumpTo(index: Storyboard.Index): Boolean {
        if (_storyboard == null) return false // Cannot jump without Storyboard.

        val frame = byIndex[index]
        if (frame == null) return false

        targetIndex = frame.storyboardIndex
        snapTo(frame.frameIndex)

        currentIndex = targetIndex
        return true
    }

    private suspend fun snapTo(targetState: Int) {
        frameIndex.snapTo(targetState)

        // TODO this is a workaround for SeekableTransitionState not supporting
        //  `snapTo` without a transition.
        // TODO report a bug?
        if (frameIndex.currentState != targetState) {
            frameIndex = SeekableTransitionState(targetState)
        }
    }

    internal suspend fun seek(fraction: Float) {
        if (_storyboard == null) return // Cannot seek without Storyboard.
        val progress = fraction * frames.lastIndex
        val index = progress.toInt()
        val fraction = progress - index

        if (frameIndex.currentState != index) {
            frameIndex.snapTo(index)

            var currentState = index
            while (currentState >= 0 && frames[currentState].frame !is Frame.State) currentState--
            currentIndex = frames[currentState].storyboardIndex

            var targetState = index + if (fraction > 0f) 1 else 0
            while (targetState < frames.size && frames[targetState].frame !is Frame.State) targetState++
            targetIndex = frames[targetState].storyboardIndex
        }

        // TODO there seems to be some serious bugs with SeekableTransitionState still...
        //  - seeking backwards seems to be broken for the opening animation
        //  - seeking backwards in general actually seems a little broken
        //  maybe I can create a minimal reproducer?
        frameIndex.seekTo(fraction, index + 1)
    }

    @ExperimentalStoryStateApi
    // Perform without read observation, so reads do not cause a state reset.
    fun updateStoryboard(storyboard: Storyboard) = Snapshot.withoutReadObservation {
        if (this._storyboard == storyboard) return

        this._storyboard = storyboard
        this.frames = buildFrames(storyboard.scenes)
        this.byIndex = frames.filter { it.storyboardIndex.stateIndex >= 0 }.associateBy { it.storyboardIndex }

        val currentIndex = currentIndex
        val frame = byIndex[currentIndex]
        if (frame == null) {
            val searchIndex = frames.binarySearch { it.storyboardIndex.compareTo(currentIndex) }
            require(searchIndex < 0) { "current index not expected to be in storyboard: $currentIndex" }

            // Back up from the search index until a state frame is found.
            // Since the first frame of a storyboard is always a state frame,
            // this loop will never run out of bounds.
            var initialFrameIndex = -searchIndex - 1
            while (frames[initialFrameIndex].frame !is Frame.State) {
                initialFrameIndex--
            }

            val storyboardIndex = frames[initialFrameIndex].storyboardIndex
            this.currentIndex = storyboardIndex
            this.targetIndex = storyboardIndex
            this.frameIndex = SeekableTransitionState(initialFrameIndex)
        }
    }

    private fun buildFrames(scenes: List<Scene<*>>) = buildList {
        require(scenes.isNotEmpty()) { "cannot build frames for empty list of scenes" }

        val first = scenes.first()
        val last = scenes.last()
        require(first.states.isNotEmpty() && last.states.isNotEmpty()) { "first and last scene must have states" }

        var frameIndex = 0
        fun <T> MutableList<StateFrame<*>>.addStates(scene: Scene<T>) {
            for ((stateIndex, state) in scene.states.withIndex()) {
                val index = StateFrame(
                    frameIndex = frameIndex++,
                    scene = scene,
                    frame = Frame.State(state),
                    storyboardIndex = Storyboard.Index(scene.index, stateIndex),
                )
                add(index)
            }
        }

        for ((sceneIndex, scene) in scenes.withIndex()) {
            if (scene != first) {
                // TODO don't create an invalid Storyboard.Index
                add(
                    StateFrame(
                        frameIndex = frameIndex++,
                        scene = scene,
                        frame = Frame.Start,
                        storyboardIndex = Storyboard.Index(sceneIndex, -1)
                    )
                )
            }
            addStates(scene)
            if (scene != last) {
                // TODO don't create an invalid Storyboard.Index
                add(
                    StateFrame(
                        frameIndex = frameIndex++,
                        scene = scene,
                        frame = Frame.End,
                        storyboardIndex = Storyboard.Index(sceneIndex, scene.states.size)
                    )
                )
            }
        }
    }

    internal class StateFrame<T>(
        val frameIndex: Int,
        val scene: Scene<T>,
        val frame: Frame<T>,
        val storyboardIndex: Storyboard.Index,
    )

    @Composable
    internal fun rememberTransition(): Transition<StateFrame<*>> {
        return rememberTransition(frameIndex)
            .createChildTransition { frames[it] }
    }
}

private fun AdvanceDirection.toInt(): Int = when (this) {
    AdvanceDirection.Forward -> 1
    AdvanceDirection.Backward -> -1
}

private fun <T : Comparable<T>> toDirection(current: T, target: T): AdvanceDirection? {
    val compare = current.compareTo(target)
    return when {
        compare < 0 -> AdvanceDirection.Forward
        compare > 0 -> AdvanceDirection.Backward
        else -> null
    }
}
