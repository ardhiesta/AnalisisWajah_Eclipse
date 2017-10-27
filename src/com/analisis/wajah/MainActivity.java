package com.analisis.wajah;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.List;
import com.analisa.wajah.R;
import clarifai2.api.ClarifaiBuilder;
import clarifai2.api.ClarifaiClient;
import clarifai2.dto.input.ClarifaiInput;
import clarifai2.dto.model.output.ClarifaiOutput;
import clarifai2.dto.prediction.Concept;
import clarifai2.dto.prediction.Region;
import android.support.v7.app.ActionBarActivity;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends ActionBarActivity {
	// tombol untuk memilih foto dari gallery
	Button btnPilihFoto; 
	// TextView untuk menampilkan hasil analisis foto
	TextView tvHasilAnalisisFoto;
	// ImageView untuk menampilkan thumbnail foto
	ImageView ivThumbnailFoto; 
	// progressBar untuk menampilkan animasi proses loading
	ProgressBar pbLoadingAnalisisFoto; 
	// object bitmap untuk menampilkan foto yang dipilih
	Bitmap bitmapFoto; 
	// info tag memilih image dari gallery untuk API < 19 (Android sebelum KitKat)
	private static final int PICK_IMAGE_BELLOW_API19 = 1;
	// info tag memilih image dari gallery untuk API 19
	private static final int PICK_IMAGE_API19 = 2; 

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// menghubungkan komponen UI dari layout
		tvHasilAnalisisFoto = (TextView) findViewById(R.id.text_hasil);
		btnPilihFoto = (Button) findViewById(R.id.btnTakePic);
		ivThumbnailFoto = (ImageView) findViewById(R.id.ivThumbnailPhoto);
		pbLoadingAnalisisFoto = (ProgressBar) findViewById(R.id.pb_loading);

		btnPilihFoto.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				// action memilih image dari gallery setelah mengklik tombol
				pilihFotoKlik();
			}
		});
	}

	private void pilihFotoKlik() {
		if (Build.VERSION.SDK_INT < 19) {
			// action memilih image dari gallery untuk versi Android sebelum
			// KitKat
			Intent intent = new Intent(Intent.ACTION_PICK,
					MediaStore.Images.Media.INTERNAL_CONTENT_URI);
			intent.setType("image/*");
			intent.putExtra("return-data", true);
			startActivityForResult(intent, PICK_IMAGE_BELLOW_API19);
		} else {
			k// action memilih image dari gallery untuk versi Android KitKat
			// memilih image menggunakan Storage Access Framework
			Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			intent.setType("image/*");
			startActivityForResult(intent, PICK_IMAGE_API19);
		}
	}

	// method untuk menampilkan foto yang dipilih ke tampilan thumbnail gambar
	// pada Android KitKat
	private Bitmap getBitmapFromUri(Uri uri) throws IOException {
		ParcelFileDescriptor parcelFileDescriptor = getContentResolver()
				.openFileDescriptor(uri, "r");
		FileDescriptor fileDescriptor = parcelFileDescriptor
				.getFileDescriptor();
		Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
		parcelFileDescriptor.close();
		return image;
	}

	// action yang dilakukan setelah memilih file foto
	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		if (requestCode == PICK_IMAGE_API19 && resultCode == Activity.RESULT_OK
				&& intent != null) {
			Uri uri = intent.getData();
			try {
				Bitmap bitmap = getBitmapFromUri(uri);
				
				// menampilkan thumbnail foto
				ivThumbnailFoto.setImageBitmap(bitmap);

				String realPath;
				
				// mengambil path foto yang dipilih
				// < API 11
				if (Build.VERSION.SDK_INT < 11)
					realPath = RealPathUtil.ambilPathDariURI_diBawahAPI_11(
							this, intent.getData());

				// API >= 11 && API < 19
				else if (Build.VERSION.SDK_INT < 19)
					realPath = RealPathUtil.ambilPathDariURI_API_11_18(this,
							intent.getData());

				// API => 19 (Android 4.4)
				else
					realPath = RealPathUtil.ambilPathDariURI_API_19(this,
							intent.getData());

				// eksekusi class AnalisisFotoTask untuk memanggil API Clarifai
				AnalisisFotoTask myTask = new AnalisisFotoTask();
				myTask.execute(realPath, null, null);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (requestCode == PICK_IMAGE_BELLOW_API19
				&& resultCode == Activity.RESULT_OK && intent != null) {
			Bitmap bitmap = intent.getExtras().getParcelable("data");
			ivThumbnailFoto.setImageBitmap(bitmap);
			String realPath;
			// SDK < API11
			if (Build.VERSION.SDK_INT < 11)
				realPath = RealPathUtil.ambilPathDariURI_diBawahAPI_11(this,
						intent.getData());

			// SDK >= 11 && SDK < 19
			else if (Build.VERSION.SDK_INT < 19)
				realPath = RealPathUtil.ambilPathDariURI_API_11_18(this,
						intent.getData());

			// SDK => 19 (Android 4.4)
			else
				realPath = RealPathUtil.ambilPathDariURI_API_19(this,
						intent.getData());

			// eksekusi class AnalisisFotoTask untuk memanggil API Clarifai
			AnalisisFotoTask analisisFotoTask = new AnalisisFotoTask();
			analisisFotoTask.execute(realPath, null, null);
		}
	}

	// class asyncTask untuk memanggil API Clarifai
	private class AnalisisFotoTask extends AsyncTask<String, Void, Void> {
		@Override
		protected void onPreExecute() {
			// menampilkan animasi progressBar loading
			setLoading(true);
		};

		@Override
		protected Void doInBackground(String... arg0) {
			ClarifaiClient client = new ClarifaiBuilder(
					"API_KEY_CLARIFAI" 
					).buildSync();

			// memanggil API Clarifai untuk memprediksi fitur demographic
			// dari foto ang dipilih
			List<ClarifaiOutput<Region>> predictionResults = client
					.getDefaultModels()
					.demographicsModel()
					.predict()
					.withInputs(
							ClarifaiInput.forImage(new File(arg0[0].toString())))
					.executeSync().get();

			if (predictionResults.size() > 0) {
				if (predictionResults.get(0).data().size() > 0) {
					// mengambil informasi usia hasil prediksi dari API Clarifai
					List<Concept> ageList = predictionResults.get(0).data()
							.get(0).ageAppearances();
					
					// mengambil informasi jenis kelamin hasil prediksi dari API Clarifai
					List<Concept> genderList = predictionResults.get(0).data()
							.get(0).genderAppearances();
					
					// mengambil informasi jenis ras hasil prediksi dari API Clarifai
					List<Concept> appearancesList = predictionResults.get(0)
							.data().get(0).multiculturalAppearances();

					// mengubah tampilan jenis kelamin yang muncul ke bahasa Indonesia
					String jenisKelamin = genderList.get(0).name();
					if (jenisKelamin.equalsIgnoreCase("masculine")) {
						jenisKelamin = "laki-laki";
					} else {
						jenisKelamin = "perempuan";
					}

					// menampilkan informasi hasil analisis foto ke tampilan program
					setTextHasil(tvHasilAnalisisFoto,
							"Hasil analisis foto \n Usia: "
									+ ageList.get(0).name()
									+ " tahun\nJenis kelamin: " + jenisKelamin
									+ "\nRas: " + appearancesList.get(0).name());
				} else {
					// menampilkan informasi gagal menganalisis foto ke tampilan program
					setTextHasil(tvHasilAnalisisFoto, "gagal menganalisis foto");
				}
			} else {
				// menampilkan informasi gagal menganalisis foto ke tampilan program
				setTextHasil(tvHasilAnalisisFoto, "gagal menganalisis foto");
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			// mengakhiri animasi loading
			setLoading(false);
		}
	}

	// method setLoading untuk mengatur tampilan ketika aplikasi memanggil API
	private void setLoading(boolean status) {
		// progressBar dianimasikan (berputar)
		pbLoadingAnalisisFoto.setIndeterminate(status);
		if (status) {
			// jika aplikasi sedang memanggil API Clarifai
			// progressBar ditampilkan, komponen lain disembunyikan
			pbLoadingAnalisisFoto.setVisibility(View.VISIBLE);
			btnPilihFoto.setVisibility(View.GONE);
			tvHasilAnalisisFoto.setVisibility(View.GONE);
			ivThumbnailFoto.setVisibility(View.GONE);
		} else {
			// jika aplikasi telah selesai memanggil API Clarifai
			// progressBar disembunyikan, komponen lain ditampilkan
			pbLoadingAnalisisFoto.setVisibility(View.GONE);
			btnPilihFoto.setVisibility(View.VISIBLE);
			tvHasilAnalisisFoto.setVisibility(View.VISIBLE);
			ivThumbnailFoto.setVisibility(View.VISIBLE);
		}
	}

	// menampilkan hasil analisis foto ke TextView
	private void setTextHasil(final TextView text, final String value) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				text.setText(value);
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
/*
 * http://hmkcode.com/android-camera-taking-photos-camera/
 * http://hmkcode.com/android-display-selected-image-and-its-real-path/
 * https://developer.android.com/guide/topics/providers/document-provider.html
 */
