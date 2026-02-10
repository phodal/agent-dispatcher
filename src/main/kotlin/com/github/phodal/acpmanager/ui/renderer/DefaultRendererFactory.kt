package com.github.phodal.acpmanager.ui.renderer

import com.intellij.openapi.project.Project

/**
 * Default factory for creating AcpEventRenderer instances.
 */
class DefaultRendererFactory : AcpEventRendererFactory {
    override fun createRenderer(
        agentKey: String,
        scrollCallback: () -> Unit,
        project: Project?,
        eventCallback: ((RenderEvent) -> Unit)?
    ): AcpEventRenderer {
        return DefaultAcpEventRenderer(agentKey, scrollCallback)
    }
}

/**
 * Initialize the default renderer factory.
 * Call this during plugin initialization.
 */
fun initializeDefaultRendererFactory() {
    AcpEventRendererRegistry.setDefaultFactory(DefaultRendererFactory())
}

