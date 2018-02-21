package com.sernic.uninstallsystemapps;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.Toast;


import com.chrisplus.rootmanager.RootManager;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

import static android.widget.GridLayout.HORIZONTAL;
import static android.widget.GridLayout.VERTICAL;

public class MainActivity extends AppCompatActivity {
    private ArrayList<App> mApps, tempMApps;
    private RecyclerView mRecyclerView;
    private View view;
    private Boolean rootAccess;
    // Unique request code.
    private static final int WRITE_REQUEST_CODE = 43;
    private static final int READ_REQUEST_CODE = 42;
    private static final String KEY_PREF_HIDE_SYSTEM_APPS = "hide_system_apps";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        view = findViewById(R.id.coordinatorLayout);
        mRecyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);
        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Load apps
        SearchApp searchApp = new SearchApp(this);
        searchApp.execute();

        // Obtain root
        final ObtainRoot obtainRoot = new ObtainRoot(this);
        obtainRoot.execute();
        // execute...

        // Azioni floating button
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(rootAccess) {
                    // Elimino le app se almeno una è selezionata
                    if(((MyAdapter)mRecyclerView.getAdapter()).getCheckOneApp() == 0) {
                        Snackbar.make(view, "Nessuna app selezionata!", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    } else {
                        RemoveApps removeApps = new RemoveApps(MainActivity.this);
                        removeApps.execute();
                    }
                } else {
                    Snackbar.make(view, "Non posso eliminare le app senza i permessi di root!", Snackbar.LENGTH_LONG).setAction("Ottieni", new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    ObtainRoot obtainRoot = new ObtainRoot(MainActivity.this);
                                    obtainRoot.execute();
                                }
                            }).show();
                }
            }
        });

        // Animazione del floatingActionButton durante lo scroll
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0 && fab.getVisibility() == View.VISIBLE) {
                    fab.hide();
                } else if (dy < 0 && fab.getVisibility() != View.VISIBLE) {
                    fab.show();
                }
            }
        });
    }




    // Search view
    @Override
    public boolean onCreateOptionsMenu( Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);

        MenuItem actionSearch = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) actionSearch.getActionView();
        searchView.setIconifiedByDefault(true);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (TextUtils.isEmpty(newText)) {
                    filter("");
                    //listView.clearTextFilter();
                } else {
                    filter(newText);
                }
                return true;
            }
        });
        // setto la checkBox con il valore memorizzato
        MenuItem hideSystemApps = menu.findItem(R.id.hide_system_apps);
        hideSystemApps.setChecked(readBooleanPreference(KEY_PREF_HIDE_SYSTEM_APPS, false));

        return true;
    }


    // Menu item
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id=item.getItemId();
        switch (id) {
            case R.id.import_menu:
                performFileSearch();
                break;
            case R.id.export_menu:
                if(((MyAdapter)mRecyclerView.getAdapter()).getCheckOneApp() == 0) {
                    Snackbar.make(view, "Nessuna app selezionata!", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                } else {
                    SimpleDateFormat formatter = new SimpleDateFormat("dd_MM_yyyy_HH_mm_ss", Locale.ITALY);
                    Date now = new Date();
                    createFile("text/uninsSystemApp", formatter.format(now) + ".uninsSystemApp");
                }
                break;
            case R.id.hide_system_apps:
                if(item.isChecked()){
                    item.setChecked(false);
                    hideApp(true, false);
                    saveBooleanPreference(KEY_PREF_HIDE_SYSTEM_APPS, false);
                } else {
                    item.setChecked(true);
                    hideApp(true, true);
                    saveBooleanPreference(KEY_PREF_HIDE_SYSTEM_APPS, true);
                }
                break;
            case R.id.settings_menu:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;

        }
        return false;
    }

    // Toglie dalla recyclerView la tipologia di app indicata nei parametri
    private void hideApp(Boolean toSystem, Boolean toHide) {
        if(toHide) {
            for(App app : mApps) {
                if (toSystem == app.isSystemApp()) {
                    app.setSelected(false);
                    tempMApps.remove(app);
                }
            }
        } else if(mApps.size() != tempMApps.size()){
            for(App app : mApps) {
                if (toSystem == app.isSystemApp()) {
                    tempMApps.add(app);
                }
            }
        }

        // Sort apps in alphabetical order
        Collections.sort(tempMApps, new Comparator<App>() {
            @Override
            public int compare(App app, App t1) {
                return app.getName().compareToIgnoreCase(t1.getName());
            }
        });
        ((MyAdapter)mRecyclerView.getAdapter()).updateList(tempMApps);
    }

    private void saveBooleanPreference(String key, Boolean value) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    private Boolean readBooleanPreference(String key, Boolean defaultValue) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return sharedPreferences.getBoolean(key, defaultValue);
    }

    public void setApplicationList(ArrayList<App> apps) {
        mApps = new ArrayList<>(apps);
        tempMApps = new ArrayList<>(apps);
        // Nascondo le app in base al valore della checkbox salvato
        hideApp(true, readBooleanPreference(KEY_PREF_HIDE_SYSTEM_APPS, false));
    }

    public ArrayList<App> getApplicationList() {
        return mApps;
    }

    public void setRootAccess(Boolean rootAccess) {
        this.rootAccess = rootAccess;
    }

    // Filtra le app e aggiorna la recyclerView per la floatingSearchBar
    void filter(String text) {
        ArrayList<App> temp = new ArrayList();

        for(App app: tempMApps){
            if(app.getName().toLowerCase().contains(text.toLowerCase()))
                temp.add(app);
        }
        // Update recyclerview
        ((MyAdapter)mRecyclerView.getAdapter()).updateList(temp);
    }


    // Apra il browser per far selezionare all'utente il file
    public void performFileSearch() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Filter to show only images, using the image MIME data type.
        intent.setType("text/uninsSystemApp");
        startActivityForResult(intent, READ_REQUEST_CODE);
    }


    // Apre il browser per salvare il file
    private void createFile(String mimeType, String fileName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);

        // Filter to only show results that can be "opened"
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Create a file with the requested MIME type.
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, WRITE_REQUEST_CODE);
    }


    // Crea o carica il file in base al codice che gli arriva
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if(resultCode == Activity.RESULT_OK && resultData != null) {
            switch (requestCode) {
                case READ_REQUEST_CODE:
                    try {
                        readFileContent(resultData.getData());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case WRITE_REQUEST_CODE:
                    writeFileContent(resultData.getData());
                    break;
            }
        }
    }


    // Scrivo all'inerno del file appena creato le app selezionate
    private void writeFileContent(Uri uri) {
        String selectedApp = "";

        int count = 0;
        for(App app: mApps) {
            if(app.isSelected()) {
                selectedApp = selectedApp + app.getPackageName() + ",";
                count++;
            }
        }
        try{
            ParcelFileDescriptor pfd = this.getContentResolver().openFileDescriptor(uri, "w");
            FileOutputStream fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
            fileOutputStream.write(selectedApp.getBytes());
            fileOutputStream.close();
            pfd.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Snackbar.make(view, "Esportazione di " + count + " app completata!", Snackbar.LENGTH_LONG).setAction("Action", null).show();
    }

    
    // Leggo le app selezionate all'interno del file che mi viene passato e aggiorno la recyclerView
    private void readFileContent(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String currentline;
        while ((currentline = reader.readLine()) != null) {
            stringBuilder.append(currentline);
        }
        inputStream.close();
        ArrayList<String> selectedApp = new ArrayList<String>(Arrays.asList(stringBuilder.toString().split(",")));

        int count = 0;
        for(App app : mApps) {
            if(selectedApp.contains(app.getPackageName())) {
                app.setSelected(true);
                ((MyAdapter)mRecyclerView.getAdapter()).increaseCheckOneApp();
                count++;
            } else {
                app.setSelected(false);
            }
        }
        mRecyclerView.getAdapter().notifyDataSetChanged();

        if(count == 0) {
            Snackbar.make(view, "Nessuna app selezionata presente!", Snackbar.LENGTH_LONG).setAction("Action", null).show();
        } else {
            Snackbar.make(view, count + " app importate!", Snackbar.LENGTH_LONG).setAction("Action", null).show();
        }
    }

    // Controll back press to exit
    boolean doubleBackToExitPressedOnce = false;

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }
        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Premi due volte per uscire!", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                doubleBackToExitPressedOnce=false;
            }
        }, 2500);
    }
}