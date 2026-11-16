package studio.pianyu.verifier;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Framework-only verifier: tests the shrunk release without a debug Kotlin classpath. */
public final class ReleaseVerifier extends Instrumentation {
    private static final String APP = "studio.pianyu.app";
    private final StringBuilder evidence = new StringBuilder();
    private boolean publicSmoke;
    private boolean importedStudy;
    private boolean batchUi;
    private boolean filmPrintUi;
    @Override public void onCreate(Bundle args) { super.onCreate(args); publicSmoke = args != null && "public-smoke".equals(args.getString("mode")); importedStudy = args != null && "imported-study".equals(args.getString("mode")); batchUi = args != null && "batch-ui".equals(args.getString("mode")); filmPrintUi = args != null && "film-print-ui".equals(args.getString("mode")); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (importedStudy) {
                exportImportedStudy(); result.putString("stream", evidence.toString());
                finish(Activity.RESULT_OK, result); return;
            }
            evidence.append(shell("am start -W -n " + APP + "/.MainActivity"));
            waitPackage(APP);
            if (batchUi) {
                batchUi(); result.putString("stream", evidence.toString());
                writeReport(); finish(Activity.RESULT_OK, result); return;
            }
            if (filmPrintUi) {
                filmPrintUi(); result.putString("stream", evidence.toString());
                writeReport(); finish(Activity.RESULT_OK, result); return;
            }
            if (publicSmoke) {
                smokePublicDemo(); result.putString("stream", evidence.toString());
                writeReport(); finish(Activity.RESULT_OK, result); return;
            }
            if (find(getUiAutomation().getRootInActiveWindow(), "试用示例", false) != null) click("试用示例"); else click("窗边静物 · 示例");
            waitText("日常暖色"); waitEnabled("导出"); screenshot("01-editor.png");
            click("调色"); waitText("曝光"); screenshot("02-adjustment.png");
            click("HSL分色"); waitText("红 · 饱和"); setSlider("红 · 饱和", -.4f); screenshot("02b-hsl.png");
            click("配方"); click("保存当前配方"); setEditableText("验收·日常配方"); click("保存名称");
            waitText("验收·日常配方"); click("管理配方验收·日常配方"); click("改名");
            setEditableText("验收·花窗"); click("保存名称"); waitText("验收·花窗"); screenshot("02c-recipes.png");
            click("应用配方验收·花窗"); click("对比原图"); screenshot("02d-original-compare.png"); click("返回效果");
            click("管理配方验收·花窗"); click("删除"); click("取消"); waitText("验收·花窗");
            click("管理配方验收·花窗"); click("删除"); click("确认删除");
            require(find(getUiAutomation().getRootInActiveWindow(),"验收·花窗",false)==null,"deleted recipe still visible");
            screenshot("02e-recipe-deleted.png");
            click("裁切 / 旋转"); waitText("构图"); click("重置"); SystemClock.sleep(500); waitEnabled("1:1"); click("1:1"); screenshot("03-crop-workspace.png");
            click("自由"); SystemClock.sleep(200); dragCropCorner(); screenshot("03b-free-crop-drag.png");
            setSlider("校平角度", 8f); screenshot("03c-straighten.png");
            click("右转 90°"); SystemClock.sleep(200); waitEnabled("4:5"); click("4:5"); click("水平镜像"); SystemClock.sleep(200); waitEnabled("完成"); click("完成"); click("风格"); waitText("日常暖色"); screenshot("04-cropped.png"); click("风格说明"); waitText("明白了"); screenshot("05-look-reference.png"); click("明白了"); click("复制配方");
            click("导出"); click("保存到相册"); waitText("已保存到相册 · 片屿"); screenshot("06-saved.png");
            click("导出"); click("分享");
            waitPackage("com.android.intentresolver"); waitText("Pianyu_", true);
            evidence.append("share=system chooser; no recipient selected\n");
            shell("input keyevent KEYCODE_BACK"); waitPackage(APP);
            click("‹ 照片库"); waitText("全选"); waitText("窗边静物 · 示例"); screenshot("07-library.png"); click("全选"); click("套用配方"); waitText("窗边静物 · 示例"); screenshot("08-batch-selection.png"); click("取消选择"); click("导入");
            long end = SystemClock.uptimeMillis() + 30000;
            String picker = "";
            while (SystemClock.uptimeMillis() < end) {
                picker = currentPackage();
                if (picker.contains("providers.media") || picker.contains("photopicker") || picker.contains("permissioncontroller")) break;
                SystemClock.sleep(100);
            }
            require(picker.contains("providers.media") || picker.contains("photopicker") || picker.contains("permissioncontroller"), "system photo picker missing: " + picker);
            evidence.append("picker=").append(picker).append('\n');
            shell("input keyevent KEYCODE_BACK"); waitPackage(APP);
            screenshot("09-returned.png");
            evidence.append("PASS: release semantic actions, HSL, named recipe save/rename/apply/delete, original compare, crop, MediaStore save, chooser, picker\n");
            result.putString("stream", evidence.toString());
            writeReport(); finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            evidence.append("FAIL: ").append(error).append('\n');
            describeRanges(getUiAutomation().getRootInActiveWindow());
            try { screenshot("failure.png"); } catch (Exception ignored) { }
            result.putString("stream", evidence.toString());
            try { writeReport(); } catch (Exception ignored) { }
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    /** Explicit local research mode. Never included in the normal smoke flow or main APK. */
    private void exportImportedStudy() throws Exception {
        File library = new File(getTargetContext().getFilesDir(), "library-v2.json");
        JSONObject saved;
        try (FileInputStream input = new FileInputStream(library)) {
            saved = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        JSONArray photos = saved.optJSONArray("photos");
        require(photos != null, "photo library unavailable");
        File directory = new File(getTargetContext().getExternalFilesDir(null), "imported-study-v12");
        require(directory.isDirectory() || directory.mkdirs(), "cannot create private study directory");
        JSONArray rows = new JSONArray(); int copied = 0;
        for (int index = 0; index < photos.length(); index++) {
            JSONObject photo = photos.getJSONObject(index);
            String uriString = photo.optString("uri", "");
            if (uriString.isEmpty()) continue; // Bundled demo is not a user import.
            JSONObject row = new JSONObject().put("number", index + 1)
                .put("settings", photo.optJSONObject("settings"));
            try {
                Uri uri = Uri.parse(uriString);
                String mime = getTargetContext().getContentResolver().getType(uri);
                String extension = "image/heic".equals(mime) || "image/heif".equals(mime) ? "heic"
                    : "image/png".equals(mime) ? "png" : "image/jpeg".equals(mime) ? "jpg" : "bin";
                File file = new File(directory, String.format(java.util.Locale.ROOT, "imported-%03d.%s", index + 1, extension));
                try (java.io.InputStream input = getTargetContext().getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(file)) {
                    require(input != null, "source unavailable"); input.transferTo(output);
                }
                row.put("file", file.getName()).put("mime", mime).put("bytes", file.length()); copied++;
            } catch (Exception error) {
                // Keep private URI/name/error details off the console and out of public reports.
                row.put("read_error", error.getClass().getSimpleName());
            }
            rows.put(row);
        }
        try (FileOutputStream output = new FileOutputStream(new File(directory, "manifest.json"))) {
            output.write(rows.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        evidence.append("PASS: explicitly requested local imported-photo study; copied=").append(copied)
            .append("; entries=").append(rows.length()).append("; no uploads or library edits\n");
    }
    private void smokePublicDemo() throws Exception {
        if (find(getUiAutomation().getRootInActiveWindow(), "试用示例", false) != null) click("试用示例");
        else click("打开示例");
        waitText("日常暖色"); waitEnabled("导出");
        click("调色"); click("还原全部 · 可撤销"); click("风格");
        screenshot("v032-public-default.png");
        click("风格说明"); waitText("明白了"); screenshot("v032-reference.png"); click("明白了");
        click("风格说明"); click("关闭片屿预设"); waitText("胶片模型 · 未加片屿预设");
        click("风格说明"); click("启用片屿预设"); waitEnabled("导出");
        evidence.append("finish=disabled and re-enabled via actual release UI\n");
        click("调色"); click("胶片曝光"); setSlider("胶片曝光调整", .7f); waitEnabled("导出");
        screenshot("v032-film-exposure.png"); click("重置胶片曝光"); waitEnabled("导出");
        evidence.append("film exposure=+0.7EV and reset via release UI\n");
        click("风格");
        click("风格说明"); waitEnabled("重置此风格调色"); click("重置此风格调色");
        evidence.append("recommended=reset through style description; color reset with geometry preserved\n");
        click("裁切 / 旋转"); click("自由"); click("右转 90°"); screenshot("v032-free-rotate.png"); click("重置"); click("完成");
        click("调色"); click("HSL分色调整"); waitText("红 · 饱和"); screenshot("v032-hsl.png");
        click("风格"); click("导出"); waitText("原尺寸 · 保留输入像素");
        click("PNG · 无损像素（文件较大）"); screenshot("v032-original-export.png");
        click("保存到相册"); waitText("已保存到相册 · 片屿");
        click("导出"); click("HEIF · 高效存储（设备支持时）"); screenshot("v12-heif-export.png");
        click("保存到相册"); waitText("已保存到相册 · 片屿");
        evidence.append("HEIF=release UI gallery save completed; hardware writer in shrunk build\n");
        click("导出"); click("分享编辑后照片"); waitPackage("com.android.intentresolver");
        evidence.append("share=system chooser; no recipient selected\n");
        shell("input keyevent KEYCODE_BACK"); waitPackage(APP);
        click("导出"); click("JPEG · 最高质量（仍有损）"); click("取消");
        evidence.append("PASS: v032 release public demo, recommended start, model description, HSL, free crop, rotation, original PNG and HEIF save, HEIF system share chooser\n");
    }
    private void batchUi() throws Exception {
        java.util.Set<Uri> before = queryPianyuOutputs();
        try {
            if (find(getUiAutomation().getRootInActiveWindow(), "‹ 照片库", false) != null) click("‹ 照片库");
            waitText("全选");
            if (find(getUiAutomation().getRootInActiveWindow(), "取消选择", false) != null) click("取消选择");
            click("全选");
            String selected = waitText("已选", true).getText().toString();
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("已选\\s*(\\d+)").matcher(selected);
            require(count.find(), "selected count missing: " + selected);
            int n = Integer.parseInt(count.group(1));
            require(n > 0 && n <= 30, "unsafe batch count: " + n);
            click("保存 " + n + " 张");
            waitText("原尺寸 · 保留输入像素"); click("原尺寸 · 保留输入像素");
            click("JPEG · 最高质量（仍有损）"); click("保存到相册");
            String result = "批量保存完成：已保存 " + n + " 张，失败 0 张";
            waitText(result, false, 180000L); screenshot("01-batch-result.png");
            java.util.Set<Uri> after = queryPianyuOutputs(); after.removeAll(before);
            require(after.size() == n, "expected " + n + " new owned Pianyu outputs, got " + after.size());
            for (Uri uri : after) verifyOutput(uri);
            evidence.append("PASS: batch-ui selected=").append(n).append(" new_outputs=").append(after.size()).append('\n');
        } finally {
            try {
                if (APP.equals(currentPackage())) {
                    if (find(getUiAutomation().getRootInActiveWindow(), "关闭", false) != null) click("关闭");
                    if (find(getUiAutomation().getRootInActiveWindow(), "取消选择", false) != null) click("取消选择");
                }
            } catch (Throwable cleanupError) {
                evidence.append("batch_ui_restore_error=").append(cleanupError.getClass().getSimpleName()).append('\n');
            }
            try {
                java.util.Set<Uri> added = queryPianyuOutputs(); added.removeAll(before);
                int deleted = 0;
                for (Uri uri : added) if (getTargetContext().getContentResolver().delete(uri, null, null) == 1) deleted++;
                evidence.append("batch_cleanup_deleted=").append(deleted).append('\n');
            } catch (Throwable cleanupError) {
                evidence.append("batch_cleanup_error=").append(cleanupError.getClass().getSimpleName()).append('\n');
            }
        }
    }
    private void filmPrintUi() throws Exception {
        if (find(getUiAutomation().getRootInActiveWindow(), "‹ 照片库", false) != null) click("‹ 照片库");
        if (find(getUiAutomation().getRootInActiveWindow(), "试用示例", false) != null) click("试用示例");
        else if (find(getUiAutomation().getRootInActiveWindow(), "打开示例", false) != null) click("打开示例");
        waitEnabled("导出"); click("风格"); clickLook("日常暖色"); waitEnabled("导出");
        click("风格说明"); waitEnabled("重置此风格调色"); click("重置此风格调色"); waitEnabled("导出");
        click("调色"); click("胶片曝光"); waitText("自动测光");
        String[] metering = {"均衡", "中心", "保护高光", "保护阴影"};
        for (String method : metering) {
            click("选择测光方式"); click(method); SystemClock.sleep(250); waitText(method); waitEnabled("导出");
        }
        clickMeterSwitch(); waitText("关闭自动测光可保留原片曝光起点", true); clickMeterSwitch(); waitText("保护阴影");
        screenshot("01-metering.png");
        click("相纸与印放"); waitText("相纸与印放");
        click("选择冲印材料"); click("Kodak Ultra Endura"); waitText("Kodak Ultra Endura");
        click("选择冲印材料"); click("Fujifilm Crystal Archive Type II"); waitText("Fujifilm Crystal Archive Type II");
        setSlider("印放曝光调整", .4f); click("恢复推荐冲印 · 可撤销"); screenshot("02-paper-print.png");
        click("返回基础调色"); click("风格"); clickLook("自然正片"); waitText("自然正片");
        click("调色"); click("相纸与印放"); waitText("正片直接扫描 · 不经过相纸"); screenshot("03-positive-direct-scan.png");
        click("返回基础调色"); click("风格"); clickLook("日常暖色"); waitText("日常暖色");
        click("风格说明"); waitEnabled("重置此风格调色"); click("重置此风格调色"); waitText("日常暖色"); screenshot("04-restored-daily.png");
        evidence.append("PASS: film-print-ui metering=4 methods, toggle, paper stocks, +0.4 EV/reset, positive direct scan, daily restored\n");
    }
    private void clickLook(String text) {
        SystemClock.sleep(250);
        for (int attempt = 0; attempt < 7; attempt++) {
            getUiAutomation().clearCache();
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            AccessibilityNodeInfo node = findClickableText(root, text);
            if (node != null && node.isVisibleToUser() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                evidence.append("click=").append(text).append('\n'); return;
            }
            AccessibilityNodeInfo row = findScrollable(root);
            if (row != null) row.performAction("日常暖色".equals(text)
                ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            SystemClock.sleep(250);
        }
        throw new AssertionError("look missing: " + text);
    }
    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo match = findScrollable(node.getChild(i));
            if (match != null) return match;
        }
        return null;
    }
    private void clickMeterSwitch() {
        long end = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < end) {
            getUiAutomation().clearCache();
            AccessibilityNodeInfo node = findMeterSwitch(getUiAutomation().getRootInActiveWindow());
            if (node != null && clickable(node).performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                evidence.append("meter_switch=clicked\n"); return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("meter switch click rejected");
    }
    private AccessibilityNodeInfo findMeterSwitch(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if ("自动测光".equals(String.valueOf(node.getContentDescription()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo match = findMeterSwitch(node.getChild(i));
            if (match != null) return match;
        }
        return null;
    }
    private AccessibilityNodeInfo findClickableText(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        String value = node.getText() == null ? "" : node.getText().toString();
        if (text.equals(value)) {
            AccessibilityNodeInfo candidate = node;
            while (candidate != null) {
                if (candidate.isClickable()) return candidate;
                candidate = candidate.getParent();
            }
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo match = findClickableText(node.getChild(index), text);
            if (match != null) return match;
        }
        return null;
    }
    private java.util.Set<Uri> queryPianyuOutputs() {
        java.util.Set<Uri> result = new java.util.LinkedHashSet<>();
        String[] projection = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.MediaColumns.OWNER_PACKAGE_NAME};
        String selection = MediaStore.MediaColumns.OWNER_PACKAGE_NAME + "=?";
        String[] args = {APP};
        try (android.database.Cursor cursor = getTargetContext().getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, args, null)) {
            if (cursor == null) return result;
            int id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int name = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            while (cursor.moveToNext()) if (cursor.getString(name).startsWith("Pianyu_"))
                result.add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getString(id)));
        }
        return result;
    }
    private void verifyOutput(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        try (java.io.InputStream input = getTargetContext().getContentResolver().openInputStream(uri)) {
            require(input != null, "output cannot be opened: " + uri);
            BitmapFactory.decodeStream(input, null, bounds);
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0, "output has no dimensions: " + uri);
        Bitmap bitmap;
        try (java.io.InputStream input = getTargetContext().getContentResolver().openInputStream(uri)) {
            require(input != null, "output cannot be reopened: " + uri);
            bitmap = BitmapFactory.decodeStream(input);
        }
        require(bitmap != null, "output is not readable: " + uri);
        evidence.append("output=").append(bounds.outWidth).append('x').append(bounds.outHeight).append('\n');
        bitmap.recycle();
    }
    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(command))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private String currentPackage() {
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        return root == null || root.getPackageName() == null ? "" : root.getPackageName().toString();
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String text, boolean contains) {
        if (node == null) return null;
        String value = node.getText() == null ? "" : node.getText().toString();
        String description = node.getContentDescription() == null ? "" : node.getContentDescription().toString();
        if (contains ? value.contains(text)||description.contains(text) : value.equals(text)||description.equals(text)) return node;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo match = find(node.getChild(index), text, contains);
            if (match != null) return match;
        }
        return null;
    }
    private AccessibilityNodeInfo waitText(String text) { return waitText(text, false); }
    private AccessibilityNodeInfo waitText(String text, boolean contains) {
        return waitText(text, contains, 30000L);
    }
    private AccessibilityNodeInfo waitText(String text, boolean contains, long timeoutMs) {
        long end = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < end) {
            getUiAutomation().clearCache();
            AccessibilityNodeInfo node = find(getUiAutomation().getRootInActiveWindow(), text, contains);
            if (node != null) return node;
            SystemClock.sleep(100);
        }
        throw new AssertionError("text missing: " + text + "; package=" + currentPackage());
    }
    private void waitPackage(String name) {
        long end = SystemClock.uptimeMillis() + 30000;
        while (SystemClock.uptimeMillis() < end) {
            if (name.equals(currentPackage())) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("package missing: " + name);
    }
    private AccessibilityNodeInfo clickable(AccessibilityNodeInfo node) {
        while (node != null && !node.isClickable()) node = node.getParent();
        require(node != null, "no click action"); return node;
    }
    private void waitEnabled(String text) {
        long end = SystemClock.uptimeMillis() + 30000;
        while (SystemClock.uptimeMillis() < end) {
            if (clickable(waitText(text)).isEnabled()) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("action disabled: " + text);
    }
    private void click(String text) {
        require(APP.equals(currentPackage()), "refuse action outside own app");
        AccessibilityNodeInfo node = clickable(waitText(text));
        require(node.isEnabled() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "click rejected: " + text);
        evidence.append("click=").append(text).append('\n');
    }
    private void setSlider(String description, float value) {
        AccessibilityNodeInfo node = null;
        long end=SystemClock.uptimeMillis()+15000;
        while(node==null && SystemClock.uptimeMillis()<end) {
            node=locateSlider(description);
            if(node==null)SystemClock.sleep(100);
        }
        require(node!=null,"slider missing: "+description);
        Bundle args = new Bundle(); args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value);
        if (!node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(), args)) {
            android.graphics.Rect rect = new android.graphics.Rect(); node.getBoundsInScreen(rect);
            AccessibilityNodeInfo.RangeInfo range=node.getRangeInfo();
            float minimum=range==null?-20f:range.getMin(), maximum=range==null?20f:range.getMax();
            long down=SystemClock.uptimeMillis();
            for(int i=0;i<=10;i++) {
                float fraction=.5f+((value-minimum)/(maximum-minimum)-.5f)*i/10f;
                MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),i==0?MotionEvent.ACTION_DOWN:i==10?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,rect.left+rect.width()*fraction,rect.exactCenterY(),0);
                event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                try {require(getUiAutomation().injectInputEvent(event,true), "slider touch rejected");}
                finally {event.recycle();}
                SystemClock.sleep(20);
            }
        }
        SystemClock.sleep(250);
        getUiAutomation().clearCache();
        AccessibilityNodeInfo updated=locateSlider(description);
        require(updated!=null,"updated slider missing");
        updated.refresh();
        AccessibilityNodeInfo.RangeInfo actual=updated.getRangeInfo();
        require(Math.abs(actual.getCurrent()-value)<Math.max(.01f,(actual.getMax()-actual.getMin())*.08f),"slider did not move: "+description+"="+actual.getCurrent());
        evidence.append("slider_requested=").append(description).append(':').append(value).append("; actual=").append(actual.getCurrent()).append('\n');
    }
    private AccessibilityNodeInfo findSlider(AccessibilityNodeInfo node,String description) {
        if(node==null)return null;
        String label=node.getContentDescription()==null?"":node.getContentDescription().toString();
        if(label.contains(description)) {
            AccessibilityNodeInfo range=firstRange(node);if(range!=null)return range;
        }
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo match=findSlider(node.getChild(i),description);if(match!=null)return match;
        }
        return null;
    }
    private AccessibilityNodeInfo locateSlider(String description) {
        AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
        AccessibilityNodeInfo named=findSlider(root,description);if(named!=null)return named;
        AccessibilityNodeInfo label=find(root,description,false);if(label==null)return null;
        android.graphics.Rect bounds=new android.graphics.Rect();label.getBoundsInScreen(bounds);
        AccessibilityNodeInfo nearest=closestRange(root,bounds.exactCenterY());
        return nearest!=null&&rangeDistance(nearest,bounds.exactCenterY())<=Math.max(32f,bounds.height()/2f)?nearest:null;
    }
    private float rangeDistance(AccessibilityNodeInfo node,float y) {
        android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);
        return Math.abs(bounds.exactCenterY()-y);
    }
    private AccessibilityNodeInfo closestRange(AccessibilityNodeInfo node,float y) {
        if(node==null)return null;
        AccessibilityNodeInfo best=node.getRangeInfo()!=null?node:null;
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo candidate=closestRange(node.getChild(i),y);
            if(candidate!=null&&(best==null||rangeDistance(candidate,y)<rangeDistance(best,y)))best=candidate;
        }
        return best;
    }
    private AccessibilityNodeInfo firstRange(AccessibilityNodeInfo node) {
        if(node.getRangeInfo()!=null)return node;
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo range=firstRange(node.getChild(i));if(range!=null)return range;
        }
        return null;
    }
    private void describeRanges(AccessibilityNodeInfo node) {
        if(node==null)return;
        if(node.getRangeInfo()!=null || String.valueOf(node.getClassName()).contains("SeekBar")) {
            evidence.append("rangeNode class=").append(node.getClassName()).append(" text=").append(node.getText()).append(" desc=").append(node.getContentDescription());
            if(node.getRangeInfo()!=null)evidence.append(" current=").append(node.getRangeInfo().getCurrent());
            evidence.append('\n');
        }
        for(int i=0;i<node.getChildCount();i++)describeRanges(node.getChild(i));
    }
    private AccessibilityNodeInfo editable(AccessibilityNodeInfo node) {
        if(node==null)return null;
        if(node.isEditable())return node;
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo match=editable(node.getChild(i));if(match!=null)return match;
        }
        return null;
    }
    private void setEditableText(String text) {
        require(APP.equals(currentPackage()),"refuse text outside own app");
        Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
        long end=SystemClock.uptimeMillis()+15000;
        while(SystemClock.uptimeMillis()<end) {
            AccessibilityNodeInfo node=editable(getUiAutomation().getRootInActiveWindow());
            if(node!=null) {
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                if(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)) {
                    evidence.append("text=").append(text).append('\n');return;
                }
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("text input rejected");
    }
    private void dragCropCorner() {
        require(APP.equals(currentPackage()), "gesture only in own app");
        AccessibilityNodeInfo canvas = waitText("自由裁切框，拖动四角或框内移动");
        android.graphics.Rect bounds = new android.graphics.Rect(); canvas.getBoundsInScreen(bounds);
        // Demo after geometry reset is 3:2; 1:1 chooses a centered square of its full height.
        float imageWidth = Math.min(bounds.width(), bounds.height()*1.5f);
        float imageHeight = imageWidth/1.5f;
        float x = bounds.exactCenterX()-imageHeight/2f;
        float y = bounds.exactCenterY()-imageHeight/2f;
        long down = SystemClock.uptimeMillis(); boolean accepted=true;
        for(int i=0;i<=12;i++) {
            int action=i==0?MotionEvent.ACTION_DOWN:i==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
            MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x+i*imageHeight*.012f,y+i*imageHeight*.008f,0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try { accepted &= getUiAutomation().injectInputEvent(event,true); }
            catch(SecurityException blocked) {accepted=false;break;}
            finally {event.recycle();}
            SystemClock.sleep(20);
        }
        evidence.append("free_crop_touch_injection=").append(accepted).append('\n');
    }
    private File directory() { File dir = new File(getTargetContext().getExternalFilesDir(null), batchUi ? "private-batch-release-v14" : filmPrintUi ? "release-evidence-v14-ui" : "release-evidence-v031"); dir.mkdirs(); return dir; }
    private void screenshot(String name) throws Exception {
        require(APP.equals(currentPackage()), "refuse screenshot outside own app");
        SystemClock.sleep(700);
        Bitmap bitmap = getUiAutomation().takeScreenshot(); require(bitmap != null, "screenshot failed");
        try (FileOutputStream output = new FileOutputStream(new File(directory(), name))) { require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output), "PNG failed"); }
        bitmap.recycle();
    }
    private void writeReport() throws Exception {
        try (FileOutputStream output = new FileOutputStream(new File(directory(), "result.txt"))) { output.write(evidence.toString().getBytes(StandardCharsets.UTF_8)); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}


