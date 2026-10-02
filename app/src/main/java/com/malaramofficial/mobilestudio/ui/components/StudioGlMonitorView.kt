package com.malaramofficial.mobilestudio.ui.components

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.malaramofficial.mobilestudio.engine.gpu.StudioRenderPipeline

/**
 * High-performance hardware SurfaceView embedded in Jetpack Compose.
 * Displays the real-time OpenGL ES compositor output directly from [StudioRenderPipeline].
 */
@Composable
fun StudioGlMonitorView(
    renderPipeline: StudioRenderPipeline,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        renderPipeline.onSurfaceCreated(
                            surface = holder.surface,
                            width = width.coerceAtLeast(1),
                            height = height.coerceAtLeast(1)
                        )
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        renderPipeline.onSurfaceSizeChanged(width, height)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        renderPipeline.onSurfaceDestroyed()
                    }
                })
            }
        },
        update = {
            // SurfaceView manages its own frame presentation via OpenGL swapBuffers
        }
    )
}
