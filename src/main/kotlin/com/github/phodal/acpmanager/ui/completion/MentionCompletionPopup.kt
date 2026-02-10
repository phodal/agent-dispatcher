package com.github.phodal.acpmanager.ui.completion

import com.github.phodal.acpmanager.ui.mention.MentionItem
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

private val log = logger<MentionCompletionPopup>()

/**
 * Popup for @ mention autocomplete.
 * Shows a list of available mentions and handles keyboard/mouse selection.
 */
class MentionCompletionPopup(
    private val items: List<MentionItem>,
    private val onSelect: (MentionItem) -> Unit,
    private val onClose: () -> Unit
) {
    private val list = JBList(items).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = MentionItemRenderer()
        
        // Select first item by default
        if (items.isNotEmpty()) {
            selectedIndex = 0
        }
    }

    private var popup: JBPopup? = null

    /**
     * Show the popup at the given component location.
     */
    fun show(component: Component, x: Int, y: Int) {
        val step = object : BaseListPopupStep<MentionItem>("", items) {
            override fun getTextFor(value: MentionItem): String = value.displayText

            override fun onChosen(selectedValue: MentionItem?, finalChoice: Boolean): PopupStep<*>? {
                if (selectedValue != null && finalChoice) {
                    onSelect(selectedValue)
                }
                return PopupStep.FINAL_CHOICE
            }
        }

        popup = JBPopupFactory.getInstance()
            .createListPopup(step)
            .apply {
                setRequestFocus(true)
                showInScreenCoordinates(component, java.awt.Point(x, y))
            }

        setupKeyHandling()
        setupMouseHandling()
    }

    /**
     * Close the popup.
     */
    fun close() {
        popup?.cancel()
        popup = null
        onClose()
    }

    /**
     * Get the currently selected item.
     */
    fun getSelectedItem(): MentionItem? {
        val index = list.selectedIndex
        return if (index >= 0 && index < items.size) items[index] else null
    }

    /**
     * Move selection up.
     */
    fun selectPrevious() {
        val current = list.selectedIndex
        if (current > 0) {
            list.selectedIndex = current - 1
            list.ensureIndexIsVisible(current - 1)
        }
    }

    /**
     * Move selection down.
     */
    fun selectNext() {
        val current = list.selectedIndex
        if (current < items.size - 1) {
            list.selectedIndex = current + 1
            list.ensureIndexIsVisible(current + 1)
        }
    }

    private fun setupKeyHandling() {
        list.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_UP -> {
                        selectPrevious()
                        e.consume()
                    }
                    KeyEvent.VK_DOWN -> {
                        selectNext()
                        e.consume()
                    }
                    KeyEvent.VK_ENTER -> {
                        val selected = getSelectedItem()
                        if (selected != null) {
                            onSelect(selected)
                            close()
                        }
                        e.consume()
                    }
                    KeyEvent.VK_ESCAPE -> {
                        close()
                        e.consume()
                    }
                }
            }
        })
    }

    private fun setupMouseHandling() {
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val index = list.locationToIndex(e.point)
                if (index >= 0 && index < items.size) {
                    list.selectedIndex = index
                    val selected = items[index]
                    onSelect(selected)
                    close()
                }
            }
        })
    }

    /**
     * Custom cell renderer for mention items.
     */
    private class MentionItemRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val item = value as? MentionItem ?: return super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus
            )

            val label = super.getListCellRendererComponent(
                list, item.displayText, index, isSelected, cellHasFocus
            ) as JLabel

            label.icon = item.icon
            label.text = item.displayText
            if (item.tailText != null) {
                label.text = "${item.displayText}  ${item.tailText}"
            }
            label.border = JBUI.Borders.empty(4, 4)

            return label
        }
    }
}

