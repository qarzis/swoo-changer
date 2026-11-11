package local.cardart.editor;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class MainActivity extends Activity {
    private static final String PACKAGE = "com.cardsmobile.swoo";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout page, cards, history;
    private TextView status;
    private Button refresh, choose, restore, clearHistory;
    private Art selected;
    private int foreground, accent, onAccent, surface, container;
    private boolean busy;
    private boolean globalMount;
    private static final int HISTORY_LIMIT = 12;
    private static class Art {
        final ArrayList<String> paths = new ArrayList<>();
        final String hash;
        final Bitmap image;
        final int width, height;
        Art(String hash, Bitmap image, int width, int height) {
            this.hash=hash; this.image=image; this.width=width; this.height=height;
        }
    }
    private interface Job { void run() throws Exception; }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        boolean dark = (getResources().getConfiguration().uiMode & 48) == 32;
        surface = color(dark ? "system_neutral1_900" : "system_neutral1_10");
        container = color(dark ? "system_neutral2_800" : "system_neutral2_100");
        foreground = color(dark ? "system_neutral1_50" : "system_neutral1_900");
        accent = color(dark ? "system_accent1_200" : "system_accent1_700");
        onAccent = color(dark ? "system_accent1_900" : "system_accent1_10");
        getWindow().setStatusBarColor(surface);
        getWindow().setNavigationBarColor(surface);
        if (!dark) getWindow().getDecorView().setSystemUiVisibility(8192 | 16);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(surface);
        page = new LinearLayout(this);
        page.setOrientation(1);
        page.setPadding(dp(24), dp(24), dp(24), dp(24));
        scroll.addView(page);
        setContentView(scroll);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(1);
        header.setPadding(dp(22),dp(22),dp(22),dp(22));
        GradientDrawable headerBackground = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[] {container,surface});
        headerBackground.setCornerRadius(dp(32));
        headerBackground.setStroke(dp(1),container);
        header.setBackground(headerBackground);
        TextView title = label("Swoo Changer", 34);
        title.setTypeface(Typeface.create("sans-serif-medium", 0));
        header.addView(title);
        TextView subtitle = label("Обложки банковских карт", 16);
        subtitle.setPadding(0,dp(4),0,0);
        header.addView(subtitle);
        page.addView(header);

        status = label("Открой нужную карту в Swoo Pay, затем обнови список.", 15);
        status.setPadding(dp(16),dp(12),dp(16),dp(12));
        status.setBackground(round(container,20));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams statusSpace = new LinearLayout.LayoutParams(-1,-2);
        statusSpace.topMargin=dp(16);
        statusSpace.bottomMargin=dp(16);
        page.addView(status,statusSpace);
        refresh = button("Найти обложки", false, () -> load());
        cards = new LinearLayout(this);
        cards.setOrientation(1);
        page.addView(cards);
        choose = button("Выбрать фото из галереи", true, () -> {
            Intent gallery = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            gallery.setType("image/*");
            try {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    Intent picker = new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
                    picker.setType("image/*");
                    try {
                        startActivityForResult(picker, 1);
                        return;
                    } catch (ActivityNotFoundException unavailable) {
                        // Use the device's gallery when the system photo picker is unavailable.
                    }
                }
                startActivityForResult(gallery, 1);
            } catch (ActivityNotFoundException unavailable) {
                status.setText("На устройстве нет приложения для выбора фото из галереи.");
            }
        });
        restore = button("Отменить последнюю замену", false, () -> task("Восстанавливаю обложку…", () -> {
            change(null, null);
            display(scan(), "Предыдущая обложка восстановлена. Открой Swoo Pay.");
        }));
        TextView historyTitle = label("История фото", 24);
        historyTitle.setTypeface(Typeface.create("sans-serif-medium",0));
        historyTitle.setPadding(0,dp(18),0,dp(10));
        page.addView(historyTitle);
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        history = new LinearLayout(this);
        strip.addView(history);
        page.addView(strip);
        clearHistory = button("Очистить историю", false, this::confirmClearHistory);
        renderHistory();
        controls();
    }

    private int color(String name) {
        return getColor(getResources().getIdentifier(name,"color","android"));
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable bg=new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(radius)); return bg;
    }
    private TextView label(String value, int size) {
        TextView text=new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(foreground);
        return text;
    }
    private Button button(String value, boolean primary, Runnable action) {
        Button b=new Button(this); b.setText(value); b.setAllCaps(false);
        b.setTextColor(primary ? onAccent : foreground);
        b.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x33888888),round(primary ? accent : container,28),null));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));
        p.bottomMargin=dp(12); page.addView(b,p);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void controls() {
        refresh.setEnabled(!busy);
        clearHistory.setEnabled(!busy && historyFiles().length > 0);
        clearHistory.setAlpha(clearHistory.isEnabled() ? 1f : .45f);
        choose.setEnabled(!busy && selected!=null);
        restore.setEnabled(!busy && new File(getFilesDir(),"undo/paths").isFile());
        choose.setAlpha(choose.isEnabled()?1f:.45f);
        restore.setAlpha(restore.isEnabled()?1f:.45f);
    }
    private void task(String message, Job job) {
        if (busy) return;
        busy=true; controls(); status.setText(message);
        worker.execute(() -> {
            try { job.run(); }
            catch (Exception e) { runOnUiThread(() -> status.setText("Ошибка: " + e.getMessage())); }
            finally { runOnUiThread(() -> {busy=false; controls();}); }
        });
    }
    private static String q(String value) { return "'" + value.replace("'","'\\''") + "'"; }
    private byte[] su(String cmd) throws Exception {
        if (globalMount) cmd = "nsenter -t 1 -m -- /system/bin/sh -c " + q(cmd);
        Process process = new ProcessBuilder("su","-c",cmd).redirectErrorStream(true).start();
        byte[] bytes;
        try (InputStream stream=process.getInputStream()) { ByteArrayOutputStream buffer = new ByteArrayOutputStream(); byte[] chunk = new byte[8192]; int count; while ((count = stream.read(chunk)) != -1) buffer.write(chunk, 0, count); bytes = buffer.toByteArray(); }
        if (process.waitFor()!=0) throw new IOException("Команда su завершилась ошибкой. " + new String(bytes).trim());
        return bytes;
    }
    private String primaryCache() {
        return "/data/user/" + (android.os.Process.myUid()/100000) + "/" + PACKAGE + "/app_image_cache";
    }
    private boolean isCacheImagePath(String path) {
        String name = "/[a-fA-F0-9]{32,128}\\.[0-9]+";
        return path.matches(java.util.regex.Pattern.quote(primaryCache()) + name)
            || (android.os.Process.myUid()/100000 == 0
                && path.matches(java.util.regex.Pattern.quote("/data/data/" + PACKAGE + "/app_image_cache") + name));
    }
    private ArrayList<Art> scan() throws Exception {
        if (!new String(su("id -u")).trim().equals("0")) {
            throw new IOException("root не выдан приложению Swoo Changer");
        }
        String folder = primaryCache();
        String fallback = android.os.Process.myUid()/100000 == 0 ? "/data/data/" + PACKAGE + "/app_image_cache" : folder;
        String list = "d="+q(folder)+"; if ! ls \"$d\" >/dev/null 2>&1; then d="+q(fallback)+
            "; fi; ls \"$d\" >/dev/null || exit 2; "+
            "for f in \"$d\"/*; do if [ -f \"$f\" ]; then printf '%s\\n' \"$f\"; fi; done";
        String output;
        try {
            output = new String(su(list));
        } catch (IOException local) {
            globalMount = true;
            try {
                output = new String(su(list));
            } catch (IOException global) {
                globalMount = false;
                throw new IOException("кэш Swoo Pay недоступен. Обычный root: " + local.getMessage() +
                    "\nСистемное пространство монтирования: " + global.getMessage(), global);
            }
        }
        LinkedHashMap<String,Art> images = new LinkedHashMap<>();
        int candidates = 0;
        for (String line : output.split("\\r?\\n")) {
            String path = line.trim();
            if (!isCacheImagePath(path)) continue;
            candidates++;
            byte[] data = su("head -c 12582913 " + q(path));
            if (data.length > 12582912) continue;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data,0,data.length,options);
            int w=options.outWidth, h=options.outHeight;
            if(w<200 || h<100 || w/(double)h<1.4 || w/(double)h>1.9) continue;
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) {
                hex.append(Character.forDigit((value & 255) >>> 4,16));
                hex.append(Character.forDigit(value & 15,16));
            }
            String hash = hex.toString();
            Art art = images.get(hash);
            if (art == null) {
                options.inJustDecodeBounds=false;
                options.inSampleSize=Math.max(1,Integer.highestOneBit(w/320));
                Bitmap image=BitmapFactory.decodeByteArray(data,0,data.length,options);
                if(image==null) continue;
                art = new Art(hash,image,w,h);
                images.put(hash,art);
            }
            art.paths.add(path);
        }
        if (images.isEmpty()) {
            throw new IOException("подходящих обложек в кэше нет (файлов проверено: " + candidates +
                "). Открой карту в Swoo Pay и повтори поиск.");
        }
        return new ArrayList<>(images.values());
    }
    private void load() {
        task("Ищу обложки в Swoo Pay…", () -> display(scan(),null));
    }
    private void display(ArrayList<Art> images,String message) {
        runOnUiThread(() -> {
            selected=null;
            cards.removeAllViews();
            status.setText(message!=null ? message : images.isEmpty() ?
                "В кэше нет обложек. Открой карту в Swoo Pay и повтори поиск." :
                "Выбери обложку, которую хочешь заменить.");
            for(Art art:images) {
                int number=cards.getChildCount()+1;
                LinearLayout tile=new LinearLayout(this);
                tile.setOrientation(1);
                tile.setPadding(dp(12),dp(12),dp(12),dp(12));
                tile.setBackground(round(container,24));
                tile.setFocusable(true);
                tile.setContentDescription("Обложка "+number+", копий в кэше: "+art.paths.size());

                ImageView preview=new ImageView(this) {
                    @Override protected void onMeasure(int widthSpec,int heightSpec) {
                        int width=View.MeasureSpec.getSize(widthSpec);
                        super.onMeasure(widthSpec,View.MeasureSpec.makeMeasureSpec(
                            Math.round(width * art.height / (float)art.width),View.MeasureSpec.EXACTLY));
                    }
                };
                preview.setImageBitmap(art.image);
                preview.setScaleType(ImageView.ScaleType.FIT_XY);
                preview.setBackground(round(surface,16));
                preview.setClipToOutline(true);
                preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                tile.addView(preview,new LinearLayout.LayoutParams(-1,-2));

                TextView caption=label("Обложка "+number+"  ·  "+art.width+" × "+art.height,14);
                caption.setPadding(0,dp(8),0,0);
                tile.addView(caption);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
                p.bottomMargin=dp(12);
                cards.addView(tile,p);
                tile.setOnClickListener(v -> {
                    if(busy) return;
                    selected=art;
                    for(int i=0;i<cards.getChildCount();i++) {
                        View item=cards.getChildAt(i);
                        item.setSelected(false);
                        item.setBackground(round(container,24));
                    }
                    GradientDrawable selectedBackground=round(container,24);
                    selectedBackground.setStroke(dp(3),accent);
                    tile.setBackground(selectedBackground);
                    tile.setSelected(true);
                    status.setText("Выбрана обложка "+number+" · копий в кэше: "+art.paths.size());
                    controls();
                });
            }
            controls();
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=1 || result!=RESULT_OK || data==null || selected==null) return;
        confirmPhoto(data.getData(), null);
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
            .setTitle("Очистить историю?")
            .setMessage("Все сохранённые фото будут удалены из Swoo Changer.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Очистить",(dialog,which) -> task("Очищаю историю…",() -> {
                int failed = 0;
                for (File photo : historyFiles()) {
                    if (!photo.delete() && photo.exists()) failed++;
                }
                String message = failed == 0 ? "История очищена." :
                    "Не удалось удалить фото: " + failed;
                runOnUiThread(() -> {
                    renderHistory();
                    status.setText(message);
                });
            }))
            .show();
    }

    private File[] historyFiles() {
        File folder = new File(getFilesDir(),"photo-history");
        File[] files = folder.listFiles((dir,name) -> name.endsWith(".png"));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files;
    }

    private void renderHistory() {
        history.removeAllViews();
        File[] files = historyFiles();
        if (files.length == 0) {
            TextView empty = label("Применённые фото появятся здесь", 15);
            empty.setPadding(dp(16),dp(12),dp(16),dp(12));
            empty.setBackground(round(container,20));
            history.addView(empty);
            return;
        }
        for (int i=0; i<files.length; i++) {
            File photo = files[i];
            BitmapFactory.Options thumbnail = new BitmapFactory.Options();
            thumbnail.inSampleSize = 8;
            Bitmap image = BitmapFactory.decodeFile(photo.getAbsolutePath(),thumbnail);
            if (image == null) continue;
            ImageView tile = new ImageView(this);
            tile.setBackground(round(container,18));
            tile.setClipToOutline(true);
            tile.setImageBitmap(image);
            tile.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.setContentDescription("Фото из истории " + (i+1));
            LinearLayout.LayoutParams space = new LinearLayout.LayoutParams(dp(130),dp(83));
            space.rightMargin = dp(10);
            history.addView(tile,space);
            tile.setOnClickListener(v -> {
                if (busy) return;
                if (selected == null) {
                    status.setText("Сначала выбери обложку из результатов поиска.");
                    return;
                }
                confirmPhoto(null,photo);
            });
        }
    }

    private void rememberPhoto(File original) throws IOException {
        File folder = new File(getFilesDir(),"photo-history");
        if (!folder.isDirectory() && !folder.mkdir()) throw new IOException("не удалось создать историю");
        if (original.getParentFile().equals(folder)) {
            if (!original.setLastModified(System.currentTimeMillis())) throw new IOException("не удалось обновить историю");
            return;
        }
        File photo = new File(folder,UUID.randomUUID()+".png");
        File staged = new File(folder,photo.getName()+".tmp");
        try {
            java.nio.file.Files.copy(original.toPath(),staged.toPath());
            if (!staged.renameTo(photo)) throw new IOException("не удалось сохранить фото в истории");
        } finally {
            staged.delete();
        }
        File[] files=historyFiles();
        for (int i=HISTORY_LIMIT; i<files.length; i++) files[i].delete();
    }

    private void confirmPhoto(android.net.Uri uri, File saved) {
        Art art=selected;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(1);
        panel.setPadding(dp(24),dp(24),dp(24),dp(18));
        panel.setBackground(round(container,28));
        TextView title = label("Заменить карту?",24);
        title.setTypeface(Typeface.create("sans-serif-medium",0));
        panel.addView(title);

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_XY);
        preview.setBackground(round(surface,16));
        preview.setClipToOutline(true);
        preview.setContentDescription("Предпросмотр новой обложки");
        LinearLayout.LayoutParams previewSpace = new LinearLayout.LayoutParams(-1,dp(144));
        previewSpace.topMargin=dp(16);
        panel.addView(preview,previewSpace);

        TextView detail = label("Новое фото появится на обложке. Swoo Pay закроется на время замены.",14);
        detail.setPadding(0,dp(14),0,dp(14));
        panel.addView(detail);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        panel.addView(actions);
        Button cancel = dialogButton("Отмена",false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel);
        Button apply = dialogButton("Заменить",true);
        LinearLayout.LayoutParams applySpace = new LinearLayout.LayoutParams(-2,dp(48));
        applySpace.leftMargin=dp(8);
        actions.addView(apply,applySpace);

        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(dp(420),getResources().getDisplayMetrics().widthPixels-dp(48)),-2);
        }
        worker.execute(() -> {
            try {
                ImageDecoder.Source source = saved == null ?
                    ImageDecoder.createSource(getContentResolver(),uri) : ImageDecoder.createSource(saved);
                Bitmap thumbnail = ImageDecoder.decodeBitmap(source,(decoder,info,src) ->
                    decoder.setTargetSize(480,Math.max(1,Math.round(480f * art.height / art.width))));
                runOnUiThread(() -> {
                    if (dialog.isShowing()) preview.setImageBitmap(thumbnail);
                });
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> {
                    if (dialog.isShowing()) preview.setVisibility(View.GONE);
                });
            }
        });
        apply.setOnClickListener(v -> {
            dialog.dismiss();
            task("Меняю обложку…",() -> {
                ImageDecoder.Source source = saved == null ?
                    ImageDecoder.createSource(getContentResolver(),uri) : ImageDecoder.createSource(saved);
                Bitmap decoded=ImageDecoder.decodeBitmap(source,(decoder,info,src) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    int max=Math.max(info.getSize().getWidth(),info.getSize().getHeight());
                    decoder.setTargetSampleSize(Math.max(1,(int)Math.ceil(max/2048.0)));
                });
                File replacement=new File(getCacheDir(),"replacement.png");
                File original=new File(getCacheDir(),"history-original.png");
                Bitmap scaled=Bitmap.createScaledBitmap(decoded,art.width,art.height,true);
                try {
                    savePng(scaled,replacement);
                    if (saved == null) savePng(decoded,original);
                    change(art,replacement);
                    String message="Обложка заменена. Открой Swoo Pay, чтобы проверить оба экрана.";
                    try { rememberPhoto(saved == null ? original : saved); }
                    catch (IOException e) { message="Обложка заменена, но сохранить фото в историю не удалось."; }
                    runOnUiThread(this::renderHistory);
                    display(scan(),message);
                } finally {
                    replacement.delete();
                    original.delete();
                    if (scaled != decoded) scaled.recycle();
                    decoded.recycle();
                }
            });
        });
    }

    private Button dialogButton(String text, boolean filled) {
        Button action = new Button(this);
        action.setText(text);
        action.setAllCaps(false);
        action.setMinHeight(dp(48));
        action.setMinimumHeight(dp(48));
        action.setPadding(dp(16),0,dp(16),0);
        action.setTextColor(filled ? onAccent : accent);
        action.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x33888888),
            round(filled ? accent : android.graphics.Color.TRANSPARENT,24),
            round(android.graphics.Color.WHITE,24)));
        return action;
    }

    private void savePng(Bitmap bitmap, File target) throws IOException {
        try (FileOutputStream out=new FileOutputStream(target)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG,100,out)) throw new IOException("не удалось сохранить PNG");
        }
    }

    private void change(Art art, File replacement) throws Exception {
        File undo=new File(getFilesDir(),"undo");
        if(art==null && !new File(undo,"paths").isFile()) throw new IOException("копия для восстановления отсутствует");
        File pending=new File(getFilesDir(),"pending");
        if(!pending.isDirectory() && !pending.mkdir()) throw new IOException("не удалось создать резервную копию");
        List<String> paths=art==null ? java.nio.file.Files.readAllLines(new File(undo,"paths").toPath()) : art.paths;
        if(paths.isEmpty()) throw new IOException("список файлов пуст");
        StringBuilder preflight=new StringBuilder("set -e; am force-stop --user "+(android.os.Process.myUid()/100000)+" "+PACKAGE+"; ");
        StringBuilder apply=new StringBuilder("set -e; ");
        StringBuilder rollback=new StringBuilder();
        for(int i=0;i<paths.size();i++) {
            String p=paths.get(i);
            if(!isCacheImagePath(p)) throw new IOException("неожиданный путь в кэше");
            String path=q(p);
            String backup=q(new File(pending,i+".png").getAbsolutePath());
            String input=q((art==null ? new File(undo,i+".png") : replacement).getAbsolutePath());
            if(art!=null) preflight.append("[ \"$(sha256sum ").append(path).append(" | cut -d ' ' -f 1)\" = ").append(q(art.hash)).append(" ]; ");
            preflight.append("test -f ").append(path).append("; test -s ").append(input).append("; cat ").append(path).append(" > ").append(backup).append("; ");
            apply.append("cat ").append(input).append(" > ").append(path).append("; cmp -s ").append(input).append(" ").append(path).append("; ");
            rollback.append("cat ").append(backup).append(" > ").append(path).append("; ");
        }
        if(art!=null) java.nio.file.Files.write(new File(pending,"paths").toPath(),paths);
        String probe = "test -f " + q(paths.get(0));
        try {
            su(probe);
        } catch (IOException local) {
            globalMount = true;
            try { su(probe); }
            catch (IOException global) {
                globalMount = false;
                throw new IOException("файл обложки недоступен: " + global.getMessage(), global);
            }
        }
        su(preflight.toString());
        try { su(apply.toString()); }
        catch(Exception e) { su("set -e; "+rollback); throw e; }
        if(art!=null) {
            File old=new File(getFilesDir(),"old");
            erase(old);
            if(undo.exists() && !undo.renameTo(old)) {
                su("set -e; "+rollback);
                throw new IOException("не удалось обновить резервную копию");
            }
            if(!pending.renameTo(undo)) {
                old.renameTo(undo);
                su("set -e; "+rollback);
                throw new IOException("не удалось сохранить резервную копию");
            }
            erase(old);
        } else {
            erase(undo);
            erase(pending);
        }
    }
    private void erase(File dir) {
        File[] files=dir.listFiles();
        if(files!=null) for(File file:files) file.delete();
        dir.delete();
    }
}
