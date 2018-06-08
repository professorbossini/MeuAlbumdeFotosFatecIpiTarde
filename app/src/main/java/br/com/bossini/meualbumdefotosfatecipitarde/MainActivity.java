package br.com.bossini.meualbumdefotosfatecipitarde;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView fotosRecyclerView;
    private List <Bitmap> fotos;
    private static final int REQUEST_PERMISSION_CAMERA = 2001;
    private static final int REQUEST_TAKE_PICTURE = 1001;
    private static final String PNG_EXTENSION = ".png";
    private static final long UM_MEGA = 1024 * 1024;

    private StorageReference imagesReference;
    private DatabaseReference fileNameGenerator;
    private DatabaseReference urlsReference;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(fabListener);
        configurarMinhaRecyclerView();
    }

    @Override
    protected void onStart() {
        super.onStart();
        configurarOFirebase();
    }

    private void updateRecyclerViewList (Bitmap foto){
        fotos.add(foto);
        fotosRecyclerView.getAdapter().notifyDataSetChanged();
    }

    private void upload (final Bitmap foto){
        final String chave = this.fileNameGenerator.push().getKey();
        StorageReference storageReference =
                this.imagesReference.child(chave + PNG_EXTENSION);
        byte [] bytes =
                Utils.toByteArray(foto);
        UploadTask task = storageReference.putBytes(bytes);
        task.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                updateRecyclerViewList(foto);
                Uri downloadURl = taskSnapshot.getDownloadUrl();
                saveUrlForDownload (downloadURl, chave);
                Toast.makeText(MainActivity.this,
                        getString(R.string.sucesso_no_upload),
                        Toast.LENGTH_SHORT).show();

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this,
                        getString(R.string.falha_no_upload),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveUrlForDownload (Uri url, String chave){
        this.urlsReference.child(chave).setValue(url.toString());
    }

    private void configurarOFirebase (){
        final FirebaseStorage firebaseStorage =
                FirebaseStorage.getInstance();
        //o nome images é você que escolhe
        StorageReference storageRootReference =
                firebaseStorage.getReference("images");
        FirebaseDatabase firebaseDatabase =
                FirebaseDatabase.getInstance();
        this.fileNameGenerator = firebaseDatabase.getReference("image_names");
        this.urlsReference = firebaseDatabase.getReference("urls");
        this.urlsReference.
                addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                fotos.clear();
                for (DataSnapshot filho : dataSnapshot.getChildren()){
                    String url =
                            filho.getValue() + PNG_EXTENSION;
                    StorageReference aux =
                            firebaseStorage.getReferenceFromUrl(url);
                    aux.getBytes(UM_MEGA).addOnSuccessListener(new OnSuccessListener<byte[]>() {
                        @Override
                        public void onSuccess(byte[] bytes) {
                            updateRecyclerViewList (Utils.toBitmap (bytes));
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.falha_no_download),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(MainActivity.this,
                        getString(R.string.falha_conexao_fb),
                        Toast.LENGTH_SHORT).show();
            }
        });

    }

    private View.OnClickListener fabListener =
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (ActivityCompat.
                            checkSelfPermission(MainActivity.this,
                                    Manifest.permission.CAMERA) !=
                            PackageManager.PERMISSION_GRANTED){
                        //entrei aqui, não tenho permissão ainda
                        if (ActivityCompat.
                                shouldShowRequestPermissionRationale(
                                        MainActivity.this, Manifest.permission.CAMERA )){
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.explicacao_permissao_camera),
                                    Toast.LENGTH_SHORT).show();
                        }
                        //pede permissão
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[] {Manifest.permission.CAMERA},
                                REQUEST_PERMISSION_CAMERA);

                    }
                    else{
                        //já tenho permissão
                        goTakePicture();
                    }
                }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode){
            case REQUEST_PERMISSION_CAMERA:
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    goTakePicture();
                }
                else{
                    Toast.makeText(this, getString(
                            R.string.explicacao_permissao_camera),
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TAKE_PICTURE){
            if (resultCode == Activity.RESULT_OK){
                //tirou a foto

            }
            else{
                //não tirou foto
                Toast.makeText(this,
                        getString(R.string.foto_nao_confirmada),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void goTakePicture (){
        Intent tirarFotoIntent =
                new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(tirarFotoIntent, REQUEST_TAKE_PICTURE);
    }
    private static class FotosViewHolder extends
                            RecyclerView.ViewHolder{

        private View view;
        private ImageView fotoImageView;
        public FotosViewHolder (View view){
            super(view);
            this.view = view;
            this.fotoImageView =
                    view.findViewById(R.id.fotoImageView);
        }
    }

    private class FotosAdapter extends
            RecyclerView.Adapter <FotosViewHolder>{
        private Context context;
        private List<Bitmap> fotos;
        public FotosAdapter (Context context,
                             List <Bitmap> fotos){
            this.context =
                    context;
            this.fotos =
                    fotos;
        }

        @Override
        public int getItemCount() {
            return fotos.size();
        }

        @NonNull
        @Override
        public FotosViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater li =
                    LayoutInflater.from(context);
            View v =
                    li.inflate(R.layout.fotos_layout, parent, false);
            return new FotosViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull FotosViewHolder holder, int position) {
            Bitmap foto = fotos.get(position);
            holder.fotoImageView.setImageBitmap(foto);

        }
    }

    private void configurarMinhaRecyclerView(){
        fotosRecyclerView =
                findViewById(R.id.fotosRecyclerView);
        fotos = new ArrayList<Bitmap>();
        fotosRecyclerView.setAdapter(
                new FotosAdapter(this, fotos));
        fotosRecyclerView.setLayoutManager(
                new GridLayoutManager(this, 2));
    }
}
