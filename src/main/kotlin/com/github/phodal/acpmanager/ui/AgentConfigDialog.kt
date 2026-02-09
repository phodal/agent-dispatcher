package com.github.phodal.acpmanager.ui

import com.github.phodal.acpmanager.config.AcpAgentConfig
import com.github.phodal.acpmanager.config.AcpConfigService
import com.github.phodal.acpmanager.config.AcpManagerConfig
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * Dialog for managing ACP agent configurations.
 * Left panel: Agent list with add/remove buttons
 * Right panel: Configuration details for selected agent
 */
class AgentConfigDialog(
    private val project: Project,
) : DialogWrapper(project, true) {

    private val configService = AcpConfigService.getInstance(project)
    private var currentConfig = configService.loadConfig()

    // Agent list model
    private val agentListModel = DefaultListModel<String>()
    private val agentList = JBList(agentListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }

    // Configuration fields
    private val keyField = JBTextField()

    private val commandField = JBTextField()

    private val argsField = JBTextField()

    private val descriptionField = JBTextField()

    private val envField = JTextArea().apply {
        rows = 5
        lineWrap = true
        wrapStyleWord = true
    }

    private val autoApproveCheckbox = JCheckBox("Auto-approve all permissions")

    private val allowedToolsField = JBTextField().apply {
        toolTipText = "Comma-separated list of tools to auto-approve (e.g., Bash, Read, Edit, Write)"
    }

    private val nonStandardApiCheckbox = JCheckBox("Non-standard API (Claude Code)")

    private var selectedAgentKey: String? = null
    private var isModified = false

    init {
        title = "ACP Agent Configuration"
        init()
        loadAgentList()

        // Select first agent if available
        if (agentListModel.size() > 0) {
            agentList.selectedIndex = 0
        }
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(JBUI.scale(10), 0))

        // Left panel: Agent list with toolbar
        val leftPanel = createAgentListPanel()
        leftPanel.preferredSize = Dimension(JBUI.scale(200), JBUI.scale(500))

        // Right panel: Configuration form
        val rightPanel = createConfigPanel()
        rightPanel.preferredSize = Dimension(JBUI.scale(550), JBUI.scale(500))

        mainPanel.add(leftPanel, BorderLayout.WEST)
        mainPanel.add(rightPanel, BorderLayout.CENTER)

        mainPanel.preferredSize = Dimension(JBUI.scale(800), JBUI.scale(550))
        mainPanel.border = JBUI.Borders.empty(10)

        return mainPanel
    }

    private fun createAgentListPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // Add selection listener
        agentList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = agentList.selectedValue
                if (selected != null && selected != selectedAgentKey) {
                    if (isModified) {
                        saveCurrentAgent()
                    }
                    loadAgentConfig(selected)
                }
            }
        }

        // Create toolbar with add/remove buttons
        val decorator = ToolbarDecorator.createDecorator(agentList)
            .setAddAction { addNewAgent() }
            .setRemoveAction { removeSelectedAgent() }
            .setRemoveActionUpdater { agentList.selectedValue != null }
            .createPanel()

        panel.add(JBLabel("Agents:"), BorderLayout.NORTH)
        panel.add(decorator, BorderLayout.CENTER)

        return panel
    }

    private fun createConfigPanel(): JPanel {
        // Set preferred size for text fields to fill available width
        val textFieldWidth = JBUI.scale(450)
        keyField.preferredSize = Dimension(textFieldWidth, keyField.preferredSize.height)
        commandField.preferredSize = Dimension(textFieldWidth, commandField.preferredSize.height)
        argsField.preferredSize = Dimension(textFieldWidth, argsField.preferredSize.height)
        descriptionField.preferredSize = Dimension(textFieldWidth, descriptionField.preferredSize.height)
        allowedToolsField.preferredSize = Dimension(textFieldWidth, allowedToolsField.preferredSize.height)

        val formPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Agent Key:"), keyField, 1, false)
            .addLabeledComponent(JBLabel("Command:"), commandField, 1, false)
            .addLabeledComponent(JBLabel("Arguments:"), argsField, 1, false)
            .addLabeledComponent(JBLabel("Description:"), descriptionField, 1, false)
            .addLabeledComponent(
                JBLabel("Environment Variables:"),
                JScrollPane(envField).apply {
                    preferredSize = Dimension(textFieldWidth, JBUI.scale(120))
                },
                1,
                false
            )
            .addComponent(JBLabel("<html><i>Format: KEY=VALUE, one per line</i></html>"))
            .addVerticalGap(5)
            .addComponent(autoApproveCheckbox)
            .addComponent(nonStandardApiCheckbox)
            .addVerticalGap(5)
            .addLabeledComponent(
                JBLabel("Allowed Tools (Claude Code):"),
                allowedToolsField,
                1,
                false
            )
            .addComponent(JBLabel("<html><i>Comma-separated: Bash, Read, Edit, Write, Bash(git *)</i></html>"))
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // Add change listeners to mark as modified
        val changeListener = { _: Any -> isModified = true }
        keyField.document.addDocumentListener(SimpleDocumentListener(changeListener))
        commandField.document.addDocumentListener(SimpleDocumentListener(changeListener))
        argsField.document.addDocumentListener(SimpleDocumentListener(changeListener))
        descriptionField.document.addDocumentListener(SimpleDocumentListener(changeListener))
        envField.document.addDocumentListener(SimpleDocumentListener(changeListener))
        allowedToolsField.document.addDocumentListener(SimpleDocumentListener(changeListener))
        autoApproveCheckbox.addActionListener(changeListener)
        nonStandardApiCheckbox.addActionListener(changeListener)

        val panel = JPanel(BorderLayout())
        panel.add(JBLabel("Configuration:"), BorderLayout.NORTH)
        panel.add(formPanel, BorderLayout.CENTER)
        panel.border = JBUI.Borders.emptyLeft(10)

        return panel
    }

    private fun loadAgentList() {
        agentListModel.clear()
        currentConfig.agents.keys.sorted().forEach { key ->
            agentListModel.addElement(key)
        }
    }

    private fun loadAgentConfig(key: String) {
        selectedAgentKey = key
        val config = currentConfig.agents[key] ?: return

        keyField.text = key
        keyField.isEditable = false // Can't change key of existing agent
        commandField.text = config.command
        argsField.text = config.args.joinToString(" ")
        descriptionField.text = config.description
        envField.text = config.env.entries.joinToString("\n") { "${it.key}=${it.value}" }
        autoApproveCheckbox.isSelected = config.autoApprove
        nonStandardApiCheckbox.isSelected = config.nonStandardApi
        allowedToolsField.text = config.allowedTools.joinToString(", ")

        isModified = false
    }

    private fun addNewAgent() {
        val newKey = Messages.showInputDialog(
            project,
            "Enter agent key (alphanumeric, hyphens, underscores only):",
            "New Agent",
            null
        ) ?: return

        if (!newKey.matches(Regex("[a-zA-Z0-9_-]+"))) {
            Messages.showErrorDialog(
                project,
                "Agent key must contain only alphanumeric characters, hyphens, and underscores",
                "Invalid Agent Key"
            )
            return
        }

        if (currentConfig.agents.containsKey(newKey)) {
            Messages.showErrorDialog(
                project,
                "Agent '$newKey' already exists",
                "Duplicate Agent Key"
            )
            return
        }

        // Save current agent if modified
        if (isModified && selectedAgentKey != null) {
            saveCurrentAgent()
        }

        // Add new agent with default config
        val newConfig = AcpAgentConfig(
            command = "",
            args = emptyList(),
            env = emptyMap(),
            description = "",
            autoApprove = false,
            nonStandardApi = false,
            allowedTools = emptyList()
        )

        currentConfig = currentConfig.copy(
            agents = currentConfig.agents + (newKey to newConfig)
        )

        agentListModel.addElement(newKey)
        agentList.setSelectedValue(newKey, true)

        // Enable key field for new agent
        keyField.isEditable = true
    }

    private fun removeSelectedAgent() {
        val selected = agentList.selectedValue ?: return

        val result = Messages.showYesNoDialog(
            project,
            "Are you sure you want to remove agent '$selected'?",
            "Remove Agent",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            currentConfig = currentConfig.copy(
                agents = currentConfig.agents - selected
            )

            agentListModel.removeElement(selected)
            selectedAgentKey = null
            isModified = false

            // Clear form
            if (agentListModel.size() > 0) {
                agentList.selectedIndex = 0
            } else {
                clearForm()
            }
        }
    }

    private fun saveCurrentAgent() {
        val key = selectedAgentKey ?: return

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

        val updatedConfig = AcpAgentConfig(
            command = commandField.text.trim(),
            args = args,
            env = env,
            description = descriptionField.text.trim(),
            autoApprove = autoApproveCheckbox.isSelected,
            nonStandardApi = nonStandardApiCheckbox.isSelected,
            allowedTools = allowedTools
        )

        currentConfig = currentConfig.copy(
            agents = currentConfig.agents + (key to updatedConfig)
        )

        isModified = false
    }

    private fun clearForm() {
        keyField.text = ""
        keyField.isEditable = true
        commandField.text = ""
        argsField.text = ""
        descriptionField.text = ""
        envField.text = ""
        autoApproveCheckbox.isSelected = false
        nonStandardApiCheckbox.isSelected = false
        allowedToolsField.text = ""
        isModified = false
    }

    override fun doValidate(): ValidationInfo? {
        if (selectedAgentKey != null) {
            if (commandField.text.isBlank()) {
                return ValidationInfo("Command is required", commandField)
            }
        }
        return null
    }

    override fun doOKAction() {
        // Save current agent if modified
        if (isModified && selectedAgentKey != null) {
            saveCurrentAgent()
        }

        // Save to config service
        configService.saveConfig(currentConfig)
        configService.reloadConfig()

        super.doOKAction()
    }
}

/**
 * Simple document listener helper
 */
private class SimpleDocumentListener(
    private val onChange: (Any) -> Unit
) : javax.swing.event.DocumentListener {
    override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = onChange(e ?: Unit)
    override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = onChange(e ?: Unit)
    override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = onChange(e ?: Unit)
}
