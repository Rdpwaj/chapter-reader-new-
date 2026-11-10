package com.example.ui
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
/** Persist once at the end of dragging, rather than writing storage on every pixel. */
@Composable
fun PreferenceSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0) {
    var preview by remember(value) { mutableFloatStateOf(value.coerceIn(valueRange)) }
    Slider(value = preview, onValueChange = { preview = it },
        onValueChangeFinished = { if (preview != value) onValueChange(preview) },
        valueRange = valueRange, steps = steps, modifier = modifier)
}
