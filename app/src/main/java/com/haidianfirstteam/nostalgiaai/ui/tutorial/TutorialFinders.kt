package com.haidianfirstteam.nostalgiaai.ui.tutorial

import android.view.View
import android.widget.TextView
import android.widget.ListView
import androidx.recyclerview.widget.RecyclerView

object TutorialFinders {

    fun recyclerChildAt(recyclerId: Int, index: Int): (View) -> View? = { root ->
        val rv = root.findViewById<RecyclerView>(recyclerId)
        if (rv == null || rv.childCount <= 0) null
        else rv.getChildAt(index.coerceIn(0, rv.childCount - 1))
    }

    fun recyclerChildViewById(recyclerId: Int, childIndex: Int, viewId: Int): (View) -> View? = { root ->
        val rv = root.findViewById<RecyclerView>(recyclerId)
        if (rv == null || rv.childCount <= 0) null
        else {
            val item = rv.getChildAt(childIndex.coerceIn(0, rv.childCount - 1))
            item.findViewById(viewId)
        }
    }

    fun listItemAt(listViewId: Int, index: Int): (View) -> View? = { root ->
        val lv = root.findViewById<ListView>(listViewId)
        if (lv == null || lv.childCount <= 0) null
        else lv.getChildAt(index.coerceIn(0, lv.childCount - 1))
    }

    fun bottomNavItem(bottomNavId: Int, menuItemId: Int): (View) -> View? = { root ->
        val nav = root.findViewById<View>(bottomNavId)
        if (nav == null) null
        else (findViewByIdRecursive(nav, menuItemId) ?: nav)
    }

    fun toolbarMenuItem(toolbarId: Int, menuItemId: Int): (View) -> View? = { root ->
        val tb = root.findViewById<View>(toolbarId)
        if (tb == null) null
        else (findViewByIdRecursive(tb, menuItemId) ?: tb)
    }

    fun preferenceByTitle(titleText: String): (View) -> View? = { root ->
        // PreferenceFragmentCompat uses a RecyclerView with this id.
        val rv = root.findViewById<RecyclerView>(androidx.preference.R.id.recycler_view)
            ?: findFirstRecyclerView(root)
        if (rv == null) {
            null
        } else {
            // Try visible children first.
            var found: View? = null
            for (i in 0 until rv.childCount) {
                val child = rv.getChildAt(i)
                val tv = findTextViewWithText(child, titleText)
                if (tv != null) {
                    found = child
                    break
                }
            }
            found
        }
    }

    private fun findFirstRecyclerView(root: View): RecyclerView? {
        if (root is RecyclerView) return root
        if (root !is android.view.ViewGroup) return null
        for (i in 0 until root.childCount) {
            val r = findFirstRecyclerView(root.getChildAt(i))
            if (r != null) return r
        }
        return null
    }

    private fun findTextViewWithText(root: View, text: String): TextView? {
        if (root is TextView) {
            if (root.text?.toString() == text) return root
        }
        if (root !is android.view.ViewGroup) return null
        for (i in 0 until root.childCount) {
            val tv = findTextViewWithText(root.getChildAt(i), text)
            if (tv != null) return tv
        }
        return null
    }

    private fun findViewByIdRecursive(root: View, id: Int): View? {
        if (root.id == id) return root
        if (root !is android.view.ViewGroup) return null
        for (i in 0 until root.childCount) {
            val v = findViewByIdRecursive(root.getChildAt(i), id)
            if (v != null) return v
        }
        return null
    }
}
