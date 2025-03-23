import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.createChildTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bnorm.storyboard.core.StoryboardBuilder
import dev.bnorm.storyboard.core.StoryboardState
import dev.bnorm.storyboard.core.scene
import dev.bnorm.storyboard.easel.EmbeddedStoryboard
import dev.bnorm.storyboard.easel.template.Body
import dev.bnorm.storyboard.easel.template.Header
import dev.bnorm.storyboard.easel.template.RevealEach

@OptIn(ExperimentalTransitionApi::class)
fun StoryboardBuilder.AppScene() {
    scene(stateCount = 4) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Header { Text("Application") }
            Divider(color = MaterialTheme.colors.primary, thickness = 4.dp)
            Body {
                Column(
                    Modifier.padding(vertical = 16.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val storyboard = rememberSaveable { StoryboardState(createStoryboard()) }

                    RevealEach(frame.createChildTransition { it.toState() }) {
                        item { Text("• Storyboard is ultimately just a Compose application.") }
                        item { Text("• Anything achieve with Compose, is possible in Storyboard!") }
                        item { Text("• You could even embed a Storyboard, in a Storyboard!") }
                        item {
                            MaterialTheme(colors = darkColors()) {
                                EmbeddedStoryboard(
                                    storyboard,
                                    modifier = Modifier.requiredSize(storyboard.storyboard.size / 3),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
