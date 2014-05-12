package me.yaotouwan.uicommon;

/**
 * Created by jason on 14-3-31.
 */
public class ActionSheetItem {

    String title;
    ActionSheetItemOnClickListener itemOnClickListener;

    public ActionSheetItem(String title, ActionSheetItemOnClickListener itemOnClickListener) {
        this.title = title;
        this.itemOnClickListener = itemOnClickListener;
    }

    public interface ActionSheetItemOnClickListener {
        public void onClick();
    }
}