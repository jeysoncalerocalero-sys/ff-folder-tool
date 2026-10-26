package com.fftool;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int PICK = 42;
    private TextView salida;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 40);

        TextView t = new TextView(this);
        t.setText("FF Folder Tool");
        t.setTextSize(22f);
        root.addView(t);

        root.addView(boton("1) Elegir carpeta de Free Fire", v -> elegir()));
        root.addView(boton("2) Ver archivos de la carpeta", v -> listar()));
        root.addView(boton("3) Aplicar mis archivos (assets/ff)", v -> aplicar()));
        root.addView(boton("4) Respaldar la carpeta", v -> respaldar()));

        salida = new TextView(this);
        salida.setTextSize(12f);
        salida.setTextIsSelectable(true);

        ScrollView sv = new ScrollView(this);
        sv.addView(salida, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv);

        setContentView(root);
        log("Carpeta guardada: " + prefs.getString("tree", "(ninguna)"));
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

    // ---------- elegir carpeta ----------

    private void elegir() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i, PICK);
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK && res == RESULT_OK && data != null && data.getData() != null) {
            Uri u = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(u,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception e) {
                log("Aviso: " + e);
            }
            prefs.edit().putString("tree", u.toString()).apply();
            log("Carpeta guardada OK:\n" + u);
        }
    }

    private DocumentFile carpeta() {
        String s = prefs.getString("tree", null);
        if (s == null) {
            log("Primero usa el boton 1 y elige la carpeta del juego.");
            return null;
        }
        DocumentFile d = DocumentFile.fromTreeUri(this, Uri.parse(s));
        if (d == null || !d.exists()) {
            log("La carpeta ya no existe. Vuelve a elegirla con el boton 1.");
            return null;
        }
        return d;
    }

    // ---------- acciones ----------

    private void listar() {
        DocumentFile d = carpeta();
        if (d == null) return;
        DocumentFile[] hijos = d.listFiles();
        log("--- " + d.getName() + " (" + hijos.length + " elementos) ---");
        for (DocumentFile f : hijos) {
            log((f.isDirectory() ? "[dir]  " : "[file] ") + f.getName());
        }
    }

    private void aplicar() {
        DocumentFile d = carpeta();
        if (d == null) return;
        if (!d.canWrite()) {
            log("Sin permiso de escritura. Repite el paso 1 y pulsa PERMITIR.");
            return;
        }
        try {
            String[] nombres = getAssets().list("ff");
            if (nombres == null || nombres.length == 0) {
                log("No hay archivos en assets/ff/");
                return;
            }
            for (String n : nombres) {
                DocumentFile destino = d.findFile(n);
                if (destino == null) {
                    destino = d.createFile("application/octet-stream", n);
                }
                if (destino == null) {
                    log("No pude crear: " + n);
                    continue;
                }
                try (InputStream in = getAssets().open("ff/" + n);
                     OutputStream out = getContentResolver()
                             .openOutputStream(destino.getUri(), "wt")) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                }
                log("Copiado: " + n);
            }
            log("Listo. CIERRA Free Fire por completo y vuelve a abrirlo.");
        } catch (Exception e) {
            log("Error: " + e);
        }
    }

    private void respaldar() {
        DocumentFile d = carpeta();
        if (d == null) return;
        File out = new File(getExternalFilesDir(null), "FFBackup");
        out.mkdirs();
        int n = 0;
        for (DocumentFile f : d.listFiles()) {
            if (!f.isFile()) continue;
            try (InputStream in = getContentResolver().openInputStream(f.getUri());
                 OutputStream os = new FileOutputStream(new File(out, f.getName()))) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
                n++;
            } catch (Exception e) {
                log("Error con: " + f.getName());
            }
        }
        log("Respaldados " + n + " archivos en:\n" + out.getAbsolutePath());
    }
}
