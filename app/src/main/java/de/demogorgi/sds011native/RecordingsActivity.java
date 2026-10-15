package de.demogorgi.sds011native;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/** Access to existing recordings, including files from earlier app versions. */
public final class RecordingsActivity extends Activity {
    private static final int SAVE_DOCUMENT = 2001;
    private String pendingExport;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Messdateien");
        if (state != null) {
            pendingExport = state.getString("pendingExport");
        }

        ArrayList<File> files = new ArrayList<>();
        File external = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (external != null) {
            collect(new File(external, "Feinstaub"), files);
        }
        collect(new File(getFilesDir(), "Documents/Feinstaub"), files);
        Collections.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        TextView help = new TextView(this);
        help.setText(files.isEmpty() ? "Noch keine CSV- oder KML-Dateien vorhanden."
                : "Datei antippen: öffnen, teilen oder speichern. Mit „Speichern unter …“ "
                + "kannst du sie z. B. in Downloads ablegen. KML-Dateien entstehen bei Messfahrten mit GPS.");
        layout.addView(help);

        ArrayList<String> labels = new ArrayList<>();
        for (File file : files) {
            labels.add(file.getName());
        }
        ListView list = new ListView(this);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        list.setOnItemClickListener((parent, view, position, id) -> chooseAction(files.get(position)));
        layout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
    }

    private static void collect(File directory, ArrayList<File> files) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File file : children) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (file.isFile() && (name.endsWith(".csv") || name.endsWith(".kml"))) {
                files.add(file);
            }
        }
    }

    private static String mimeType(File file) {
        return file.getName().toLowerCase(Locale.ROOT).endsWith(".kml")
                ? "application/vnd.google-earth.kml+xml" : "text/csv";
    }

    private void chooseAction(File file) {
        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(new String[]{"Öffnen mit …", "Teilen …", "Speichern unter …"},
                        (dialog, which) -> {
                            if (which == 2) {
                                saveAs(file);
                            } else {
                                launch(file, which == 1);
                            }
                        })
                .show();
    }

    private void launch(File file, boolean share) {
        if (!file.isFile()) {
            message("Die Datei ist nicht mehr verfügbar.");
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".recordings", file);
        Intent intent = new Intent(share ? Intent.ACTION_SEND : Intent.ACTION_VIEW);
        if (share) {
            intent.setType(mimeType(file));
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
        } else {
            intent.setDataAndType(uri, mimeType(file));
        }
        intent.setClipData(ClipData.newRawUri(file.getName(), uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, share ? "Datei teilen" : "Datei öffnen"));
        } catch (ActivityNotFoundException exc) {
            message("Keine passende App installiert. Du kannst die Datei mit „Speichern unter …“ ablegen.");
        }
    }

    private void saveAs(File file) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType(file));
        intent.putExtra(Intent.EXTRA_TITLE, file.getName());
        pendingExport = file.getAbsolutePath();
        try {
            startActivityForResult(intent, SAVE_DOCUMENT);
        } catch (ActivityNotFoundException exc) {
            pendingExport = null;
            message("Kein Dateiauswahldialog verfügbar.");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("pendingExport", pendingExport);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SAVE_DOCUMENT) {
            return;
        }
        String source = pendingExport;
        pendingExport = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || source == null) {
            return;
        }
        Uri destination = data.getData();
        // Document providers may write to cloud storage; keep I/O off the UI thread.
        new Thread(() -> {
            try (FileInputStream input = new FileInputStream(source);
                 OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
                if (output == null) {
                    throw new IOException("Zieldatei kann nicht geöffnet werden.");
                }
                byte[] buffer = new byte[32768];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            } catch (IOException | SecurityException exc) {
                runOnUiThread(() -> message("Speichern fehlgeschlagen: " + exc.getMessage()));
                return;
            }
            runOnUiThread(() -> message("Datei am gewählten Ort gespeichert."));
        }, "recording-export").start();
    }

    private void message(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
