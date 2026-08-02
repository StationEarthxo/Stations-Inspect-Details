package com.spinningitems;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

@Singleton
final class CollectionLogInspectManager
{
    private static final String PREFIX = "New item added to your collection log: ";
    private static final String CLICK_HINT = " (click to inspect)";
    private static final int DEFINITIONS_PER_TICK = 250;
    private static final int MAX_CLICKABLE_MESSAGES = 24;

    private final Client client;
    private final ItemInspectController inspector;
    private final Map<String, Integer> itemIdsByName = new HashMap<>();
    private final Map<MessageNode, String> pendingMessages = new LinkedHashMap<>();
    private final Map<String, Integer> clickableItems = new LinkedHashMap<String, Integer>()
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest)
        {
            return size() > MAX_CLICKABLE_MESSAGES;
        }
    };
    private int nextItemId;
    private boolean indexComplete;

    @Inject
    private CollectionLogInspectManager(Client client, ItemInspectController inspector)
    {
        this.client = client;
        this.inspector = inspector;
    }

    void onChatMessage(ChatMessage event)
    {
        String itemName = parseCollectionItem(event.getMessage());
        if (itemName == null || itemName.isEmpty() || event.getMessageNode() == null)
        {
            return;
        }

        Integer cachedId = itemIdsByName.get(normalize(itemName));
        if (cachedId != null)
        {
            makeClickable(event.getMessageNode(), itemName, cachedId);
            return;
        }
        pendingMessages.put(event.getMessageNode(), itemName);
    }

    void update()
    {
        if (!pendingMessages.isEmpty() && !indexComplete)
        {
            indexMoreItems();
        }
        resolvePendingMessages();
        bindVisibleChatLines();
    }

    void reset()
    {
        pendingMessages.clear();
        clickableItems.clear();
    }

    private void indexMoreItems()
    {
        int end = Math.min(client.getItemCount(), nextItemId + DEFINITIONS_PER_TICK);
        for (; nextItemId < end; nextItemId++)
        {
            ItemComposition composition = client.getItemDefinition(nextItemId);
            if (composition == null || composition.getName() == null || "null".equalsIgnoreCase(composition.getName()))
            {
                continue;
            }
            String key = normalize(composition.getName());
            Integer existing = itemIdsByName.get(key);
            if (existing == null || isBetterDefinition(composition, client.getItemDefinition(existing)))
            {
                itemIdsByName.put(key, nextItemId);
            }
        }
        indexComplete = nextItemId >= client.getItemCount();
    }

    private static boolean isBetterDefinition(ItemComposition candidate, ItemComposition current)
    {
        if (current == null)
        {
            return true;
        }
        boolean candidateNormal = candidate.getNote() == -1 && candidate.getPlaceholderTemplateId() == -1;
        boolean currentNormal = current.getNote() == -1 && current.getPlaceholderTemplateId() == -1;
        return candidateNormal && !currentNormal;
    }

    private void resolvePendingMessages()
    {
        Iterator<Map.Entry<MessageNode, String>> iterator = pendingMessages.entrySet().iterator();
        while (iterator.hasNext())
        {
            Map.Entry<MessageNode, String> entry = iterator.next();
            Integer itemId = itemIdsByName.get(normalize(entry.getValue()));
            if (itemId != null)
            {
                makeClickable(entry.getKey(), entry.getValue(), itemId);
                iterator.remove();
            }
            else if (indexComplete)
            {
                iterator.remove();
            }
        }
    }

    private void makeClickable(MessageNode node, String itemName, int itemId)
    {
        String formatted = PREFIX + "<col=ffb83e><u>" + itemName + "</u></col>"
            + "<col=9fdf9f>" + CLICK_HINT + "</col>";
        node.setRuneLiteFormatMessage(formatted);
        clickableItems.put(normalize(itemName), itemId);
        client.refreshChat();
    }

    private void bindVisibleChatLines()
    {
        if (clickableItems.isEmpty())
        {
            return;
        }
        Widget scrollArea = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (scrollArea == null || scrollArea.getDynamicChildren() == null)
        {
            return;
        }
        for (Widget widget : scrollArea.getDynamicChildren())
        {
            if (widget == null || widget.getText() == null)
            {
                continue;
            }
            String itemName = parseCollectionItem(widget.getText());
            if (itemName == null)
            {
                continue;
            }
            itemName = itemName.replace(CLICK_HINT, "").trim();
            Integer itemId = clickableItems.get(normalize(itemName));
            if (itemId == null || hasInspectAction(widget))
            {
                continue;
            }
            final int inspectedItemId = itemId;
            widget.setAction(0, "Inspect");
            widget.setOnOpListener((JavaScriptCallback) event -> inspector.open(inspectedItemId, true));
        }
    }

    private static boolean hasInspectAction(Widget widget)
    {
        String[] actions = widget.getActions();
        if (actions == null)
        {
            return false;
        }
        for (String action : actions)
        {
            if ("Inspect".equals(action))
            {
                return true;
            }
        }
        return false;
    }

    static String parseCollectionItem(String message)
    {
        if (message == null)
        {
            return null;
        }
        String plain = Text.removeTags(message).replace('\u00A0', ' ').trim();
        return plain.startsWith(PREFIX) ? plain.substring(PREFIX.length()).trim() : null;
    }

    private static String normalize(String value)
    {
        return value.toLowerCase(java.util.Locale.ROOT).trim();
    }
}
