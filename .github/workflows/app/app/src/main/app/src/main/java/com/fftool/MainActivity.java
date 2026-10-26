package com.fftool;

import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_FOLDER = 42;
    private static final int PICK_FILES = 43;

    private static final String FF = "com.dts.freefireth";
    private static final String FFMAX = "com.dts.freefiremax";

    private TextView salida;
    private EditText campo;
    private SharedPreferences prefs;
    private String juego = FF;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 36, 36, 36);

        TextView titulo = new TextView(this);
        titulo.setText("FF Folder Tool PRO");
        titulo.setTextSize(22f);
        root.addView(titulo);

        RadioGroup grupo = new RadioGroup(this);
        grupo.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton r1 = new RadioButton(this);
        r1.setText("Free Fire");
        r1.setId(1);
        r1.setChecked(true);
        RadioButton r2 = new RadioButton(this);
        r2.setText("FF MAX");
        r2.setId(2);
        grupo.addView(r1);
        grupo.addView(r2);
        grupo.setOnCheckedChangeListener((g, id) -> {
            juego = (id == 2) ? FFMAX : FF;
            log("Juego seleccionado: " + juego);
        });
        root.addView(grupo);

        root.addView(boton("1) Elegir carpeta del juego", v -> elegirCarpeta()));
        root.addView(boton("2) Ver archivos de la carpeta", v -> listar()));
        root.addView(boton("3) Aplicar mis archivos (assets/ff)", v -> aplicar()));
        root.addView(boton("4) Importar archivo(s) del telefono", v -> importarVarios()));
        root.addView(boton("5) Respaldar carpeta", v -> respaldar()));
        root.addView(boton("6) Restaurar respaldo", v -> restaurar()));

        campo = new EditText(this);
        campo.setHint("nombre exacto del archivo");
        root.addView(campo);
        root.addView(boton("7) Borrar ese archivo", v -> borrar()));

        salida = new TextView(this);
        salida.setTextSize(12f);
        salida.setTextIsSelectable(true);

        ScrollView sv = new ScrollView(this);
        sv.addView(salida, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv);

        setContentView(root);
        log("FF guardado: " + (prefs.getString("tree_" + FF, null) != null));
        log("FF MAX guardado: " + (prefs.getString("tree_" + FFMAX, null) != null));
    }

    private Button boton(String texto, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(texto);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private void log(String s) {
        salida.append(s + "\n");
    }

    private void elegirCarpeta() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i, PICK_FOLDER);
    }

    private void importarVarios() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, PICK_FILES);
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;

        if (req == PICK_FOLDER && data.getData() != null) {
            Uri u = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(u,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception e) {
                log("Aviso permiso: " + e);
            }
            prefs.edit().putString("tree_" + juego, u.toString()).apply();
            log("Carpeta guardada para " + juego + ":\n" + u);
        }

        if (req == PICK_FILES) {
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    importar(clip.getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                importar(data.getData());
            }
        }
    }

    private DocumentFile carpeta() {
        String s = prefs.getString("tree_" + juego, null);
        if (s == null) {
            log("Elige primero la carpeta del juego (boton 1).");
            return null;
        }
        DocumentFile d = DocumentFile.fromTreeUri(this, Uri.parse(s));
        if (d == null || !d.exists()) {
            log("La carpeta ya no existe. Vuelve a elegirla con el boton 1.");
            return null;
        }
        return d;
    }

    private boolean escribible(DocumentFile d) {
        if (!d.canWrite()) {
            log("Sin permiso de escritura. Repite el paso 1 y pulsa PERMITIR.");
            return false;
        }
        return true;
    }

    private void listar() {
        DocumentFile d = carpeta();
        if (d == null) return;
        DocumentFile[] hijos = d.listFiles();
        log("--- " + d.getName() + " : " + hijos.length + " elementos ---");
        for (DocumentFile f : hijos) {
            if (f.isDirectory()) log("[dir]  " + f.getName());
            else log("[file] " + f.getName() + "  (" + f.length() + " bytes)");
        }
    }

    private void aplicar() {
        DocumentFile d = carpeta();
        if (d == null || !escribible(d)) return;
        try {
            String[] nombres = getAssets().list("ff");
            if (nombres == null || nombres.length == 0) {
                log("No hay archivos en assets/ff/");
                return;
            }
            for (String n : nombres) escribirAsset(d, n);
            log("Listo. CIERRA Free Fire por completo y vuelve a abrirlo.");
        } catch (Exception e) {
            log("Error: " + e);
        }
    }

    private void escribirAsset(DocumentFile d, String nombre) throws Exception {
        DocumentFile destino = d.findFile(nombre);
        if (destino == null) destino = d.createFile("application/octet-stream", nombre);
        if (destino == null) {
            log("No pude crear: " + nombre);
            return;
        }
        try (InputStream in = getAssets().open("ff/" + nombre);
             OutputStream out = getContentResolver().openOutputStream(destino.getUri(), "wt")) {
            copiar(in, out);
        }
        log("Copiado: " + nombre);
    }

    private void importar(Uri u) {
        DocumentFile d = carpeta();
        if (d == null || !escribible(d)) return;
        String nombre = nombreDe(u);
        try {
            DocumentFile destino = d.findFile(nombre);
            if (destino == null) destino = d.createFile("application/octet-stream", nombre);
            if (destino == null) {
                log("No pude crear: " + nombre);
                return;
            }
            try (InputStream in = getContentResolver().openInputStream(u);
                 OutputStream out = getContentResolver().openOutputStream(destino.getUri(), "wt")) {
                copiar(in, out);
            }
            log("Importado: " + nombre);
        } catch (Exception e) {
            log("Error importando " + nombre + ": " + e);
        }
    }

    private String nombreDe(Uri u) {
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {
        }
        return "archivo_" + System.currentTimeMillis();
    }

    private void borrar() {
        DocumentFile d = carpeta();
        if (d == null || !escribible(d)) return;
        String nombre = campo.getText().toString().trim();
        if (nombre.isEmpty()) {
            log("Escribe un nombre en el campo de abajo.");
            return;
        }
        DocumentFile f = d.findFile(nombre);
        if (f == null) {
            log("No existe: " + nombre);
            return;
        }
        log(f.delete() ? ("Borrado: " + nombre) : ("No pude borrar: " + nombre));
    }

    private File dirRespaldo() {
        File f = new File(getExternalFilesDir(null), "backups/" + juego);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    private void respaldar() {
        DocumentFile d = carpeta();
        if (d == null) return;
        File out = dirRespaldo();
        File[] viejos = out.listFiles();
        if (viejos != null) {
            for (File f : viejos) f.delete();
        }
        int n = 0;
        for (DocumentFile f : d.listFiles()) {
            if (!f.isFile()) continue;
            try (InputStream in = getContentResolver().openInputStream(f.getUri());
                 OutputStream os = new FileOutputStream(new File(out, f.getName()))) {
                copiar(in, os);
                n++;
            } catch (Exception e) {
                log("Error con: " + f.getName());
            }
        }
        log("Respaldo de " + n + " archivos en:\n" + out.getAbsolutePath());
    }

    private void restaurar() {
        DocumentFile d = carpeta();
        if (d == null || !escribible(d)) return;
        File in = dirRespaldo();
        File[] archivos = in.listFiles();
        if (archivos == null || archivos.length == 0) {
            log("No hay respaldo guardado.");
            return;
        }
        for (File f : archivos) {
            try {
                DocumentFile destino = d.findFile(f.getName());
                if (destino == null) destino = d.createFile("application/octet-stream", f.getName());
                if (destino == null) continue;
                try (InputStream is = new FileInputStream(f);
                     OutputStream os = getContentResolver().openOutputStream(destino.getUri(), "wt")) {
                    copiar(is, os);
                }
                log("Restaurado: " + f.getName());
            } catch (Exception e) {
                log("Error con: " + f.getName());
            }
        }
    }

    private void copiar(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
    }
}
