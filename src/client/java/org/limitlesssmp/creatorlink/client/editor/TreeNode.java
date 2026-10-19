package org.limitlesssmp.creatorlink.client.editor;

import org.limitlesssmp.creatorlink.engine.config.ConfigEvent;
import org.limitlesssmp.creatorlink.engine.config.Configuration;
import org.limitlesssmp.creatorlink.engine.config.EventConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A row in the navigator. Wraps either a real config object or a synthetic
 * container (a list field like "conditions", or a types-map key). Children are
 * recomputed from the live model on every rebuild, so edits are always in sync.
 */
public final class TreeNode {
    public final Object value;            // config object, or the List<Object> for containers
    public final String label;
    public final int depth;
    public final boolean container;       // synthetic list/section node
    public final List<Object> parentList; // list that holds `value` (for delete/reorder), or null
    public boolean expanded;

    private TreeNode(Object value, String label, int depth, boolean container, List<Object> parentList) {
        this.value = value;
        this.label = label;
        this.depth = depth;
        this.container = container;
        this.parentList = parentList;
    }

    public static TreeNode root(EventConfiguration cfg, String fileName) {
        TreeNode n = new TreeNode(cfg, fileName, 0, false, null);
        n.expanded = true;
        return n;
    }

    public boolean hasChildren() {
        return !children().isEmpty() || container;
    }

    /** Direct children derived from the current model state. */
    @SuppressWarnings("unchecked")
    public List<TreeNode> children() {
        List<TreeNode> out = new ArrayList<>();
        if (container) {
            List<Object> list = (List<Object>) value;
            for (Object o : list) {
                out.add(new TreeNode(o, EditorModel.label(o), depth + 1, false, list));
            }
            return out;
        }
        if (value instanceof EventConfiguration cfg) {
            if (cfg.configs == null) cfg.configs = new ArrayList<>();
            if (cfg.init == null) cfg.init = new ArrayList<>();
            for (Configuration c : cfg.configs) {
                out.add(new TreeNode(c, EditorModel.label(c), depth + 1, false, (List<Object>) (List<?>) cfg.configs));
            }
            out.add(new TreeNode(cfg.init, "init", depth + 1, true, null));
            return out;
        }
        if (value instanceof Configuration c) {
            if (c.types == null) c.types = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, List<ConfigEvent>> e : c.types.entrySet()) {
                if (e.getValue() == null) e.setValue(new ArrayList<>());
                out.add(new TreeNode(e.getValue(), e.getKey(), depth + 1, true, null));
            }
            return out;
        }
        // Generic object: object fields and object-list fields become branches.
        for (EditorModel.FieldRef f : EditorModel.fieldsOf(value)) {
            if (f.kind == EditorModel.Kind.OBJECT) {
                Object child = f.get();
                if (child != null) {
                    out.add(new TreeNode(child, f.name() + ": " + EditorModel.label(child), depth + 1, false, null));
                }
            } else if (f.kind == EditorModel.Kind.OBJECT_LIST) {
                Object v = f.get();
                if (v != null && !((List<?>) v).isEmpty()) {
                    out.add(new TreeNode(v, f.name(), depth + 1, true, null));
                }
            }
        }
        return out;
    }

    /** Flattens the visible (expanded) tree into rows, preserving expansion by path. */
    public static void flatten(TreeNode node, List<TreeNode> out) {
        out.add(node);
        if (node.expanded) {
            for (TreeNode child : node.children()) {
                child.expanded = node.expandedPaths != null && node.expandedPaths.contains(pathOf(child));
                child.expandedPaths = node.expandedPaths;
                flatten(child, out);
            }
        }
    }

    // Expansion state survives rebuilds via identity-based paths on the root.
    public java.util.Set<Object> expandedPaths;

    public static Object pathOf(TreeNode n) {
        return n.value; // identity of the underlying object
    }

    public void setExpanded(boolean value) {
        this.expanded = value;
        if (expandedPaths != null) {
            if (value) expandedPaths.add(pathOf(this));
            else expandedPaths.remove(pathOf(this));
        }
    }
}
