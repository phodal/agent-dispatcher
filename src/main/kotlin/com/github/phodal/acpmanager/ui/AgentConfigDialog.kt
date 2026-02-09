package com.github.phodal.acpmanager.ui

import com.github.phodal.acpmanager.config.AcpAgentConfig
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.FormBuilder
import javax.swing.*

/**
 * Dialog for adding/editing an ACP agent configuration.
 */
class AgentConfigDialog(
    project: Project,
    private val existingKey: String? = null,
    private val existingConfig: AcpAgentConfig? = null,
) : DialogWrapper(project, true) {

    private val keyField = JBTextField(existingKey ?: "").apply {
        columns = 30
        isEditable = existingKey == null
    }

    private val commandField = JBTextField(existingConfig?.command ?: "").apply {
        columns = 30
    }

    private val argsField = JBTextField(existingConfig?.args?.joinToString(" ") ?: "").apply {
        columns = 30
    }

    private val descriptionField = JBTextField(existingConfig?.description ?: "").apply {
        columns = 30
    }

    private val envField = JTextArea(
        existingConfig?.env?.entries?.joinToString("\n") { "${it.key}=${it.value}" } ?: ""
    ).apply {
        rows = 3
        columns = 30
        lineWrap = true
    }

    private val autoApproveCheckbox = JCheckBox("Auto-approve permissions", existingConfig?.autoApprove ?: false)

    private val allowedToolsField = JBTextField(existingConfig?.allowedTools?.joinToString(", ") ?: "").apply {
        columns = 30
        toolTipText = "Comma-separated list of tools to auto-approve (e.g., Bash, Read, Edit, Write)"
    }

    init {
        title = if (existingKey != null) "Edit Agent: $existingKey" else "Add New Agent"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Agent Key:"), keyField, 1, false)
            .addLabeledComponent(JBLabel("Command:"), commandField, 1, false)
            .addLabeledComponent(JBLabel("Arguments:"), argsField, 1, false)
            .addLabeledComponent(JBLabel("Description:"), descriptionField, 1, false)
            .addLabeledComponent(JBLabel("Environment (KEY=VALUE, one per line):"), JScrollPane(envField), 1, false)
            .addComponent(autoApproveCheckbox)
            .addLabeledComponent(JBLabel("Allowed Tools (Claude Code only):"), allowedToolsField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .apply {
                border = JBUI.Borders.empty(10)
                preferredSize = java.awt.Dimension(JBUI.scale(450), JBUI.scale(350))
            }
    }

    override fun doValidate(): ValidationInfo? {
        if (keyField.text.isBlank()) {
            return ValidationInfo("Agent key is required", keyField)
        }
        if (commandField.text.isBlank()) {
            return ValidationInfo("Command is required", commandField)
        }
        if (!keyField.text.matches(Regex("[a-zA-Z0-9_-]+"))) {
            return ValidationInfo("Agent key must contain only alphanumeric characters, hyphens, and underscores", keyField)
        }
        return null
    }

    fun getAgentKey(): String = keyField.text.trim()

    fun getAgentConfig(): AcpAgentConfig {
        val args = argsField.text.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }

        val env = envField.text.trim()
            .lines()
            .filter { it.contains("=") }
            .associate {
                val parts = it.split("=", limit = 2)
                parts[0].trim() to parts.getOrElse(1) { "" }.trim()
            }

        val allowedTools = allowedToolsField.text.trim()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return AcpAgentConfig(
            command = commandField.text.trim(),
            args = args,
            env = env,
            description = descriptionField.text.trim(),
            autoApprove = autoApproveCheckbox.isSelected,
            allowedTools = allowedTools,
        )
    }
}
