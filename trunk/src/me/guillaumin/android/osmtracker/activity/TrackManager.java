package me.guillaumin.android.osmtracker.activity;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import me.guillaumin.android.osmtracker.OSMTracker;
import me.guillaumin.android.osmtracker.R;
import me.guillaumin.android.osmtracker.db.DataHelper;
import me.guillaumin.android.osmtracker.db.TrackContentProvider;
import me.guillaumin.android.osmtracker.db.TracklistAdapter;
import me.guillaumin.android.osmtracker.db.TrackContentProvider.Schema;
import me.guillaumin.android.osmtracker.exception.CreateTrackException;
import me.guillaumin.android.osmtracker.exception.ExportTrackException;
import me.guillaumin.android.osmtracker.gpx.GPXFileWriter;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.CursorAdapter;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

/**
 * Lists existing tracks.
 * 
 * @author Nicolas Guillaumin
 * 
 */
public class TrackManager extends ListActivity {
	
	private static final String TAG = TrackManager.class.getSimpleName();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.trackmanager);	
		getListView().setEmptyView(findViewById(R.id.trackmgr_empty));
		registerForContextMenu(getListView());
	}

	@Override
	protected void onResume() {
		// Tell service to stop notifying user of background activity
		sendBroadcast(new Intent(OSMTracker.INTENT_STOP_NOTIFY_BACKGROUND));

		Cursor cursor = getContentResolver().query(
				TrackContentProvider.CONTENT_URI_TRACK, null, null, null,
				Schema.COL_START_DATE + " asc");
		startManagingCursor(cursor);
		setListAdapter(new TracklistAdapter(TrackManager.this, cursor));

		super.onResume();
	}

	@Override
	protected void onPause() {
		// Tell service to notify user of background activity
		sendBroadcast(new Intent(OSMTracker.INTENT_START_NOTIFY_BACKGROUND));

		CursorAdapter adapter = (CursorAdapter) getListAdapter();
		if (adapter != null) {
			// Properly close the adapter cursor
			Cursor cursor = adapter.getCursor();
			stopManagingCursor(cursor);
			cursor.close();
			setListAdapter(null);
		}

		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.trackmgr_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.trackmgr_menu_newtrack:
			// Start track logger activity
			try {
				long trackId = createNewTrack();
				Intent i = new Intent(this, TrackLogger.class);
				i.putExtra(Schema.COL_TRACK_ID, trackId);
				startActivity(i);
			} catch (CreateTrackException cte) {
				Toast.makeText(this,
						getResources().getString(R.string.trackmgr_newtrack_error).replace("{0}", cte.getMessage()),
						Toast.LENGTH_LONG)
						.show();
			}
			break;
		case R.id.trackmgr_menu_settings:
			// Start settings activity
			startActivity(new Intent(this, Preferences.class));
			break;
		case R.id.trackmgr_menu_about:
			// Start About activity
			startActivity(new Intent(this, About.class));
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		getMenuInflater().inflate(R.menu.trackmgr_contextmenu, menu);
		menu.setHeaderTitle(R.string.trackmgr_contextmenu_title);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		
		switch(item.getItemId()) {
		case R.id.trackmgr_contextemenu_delete:
			
			// Confirm and delete selected track
			new AlertDialog.Builder(this)
				.setMessage(R.string.trackmgr_delete_confirm)
				.setCancelable(true)
				.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						
						getContentResolver().delete(
								ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, info.id),
								null, null);
						((CursorAdapter) TrackManager.this.getListAdapter()).getCursor().requery();
						dialog.dismiss();
					}
				})
				.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();				
					}
				}).create().show();
			
			break;
		case R.id.trackmgr_contextemenu_export:	
			// ProgressDialog dialog = ProgressDialog.show(this, null, "Exporting...");
			try {
				exportTrackAsGpx(info.id);
				Toast.makeText(this, getResources().getString(
					R.string.trackmgr_export_done).replace("{0}", Long.toString(info.id)),
					Toast.LENGTH_LONG)
					.show();
				// dialog.dismiss();
			} catch (ExportTrackException ete) {
				// dialog.dismiss();
				Toast.makeText(this,
						getResources().getString(R.string.trackmgr_export_error).replace("{0}", ete.getMessage()),
						Toast.LENGTH_LONG)
						.show(); 
			}
			break;
		}
		return super.onContextItemSelected(item);
	}
	
	/**
	 * Create a new track, in DB and on SD card
	 * @returns The ID of the new track
	 * @throws CreateTrackException
	 */
	private long createNewTrack() throws CreateTrackException {

		// Create directory for track
		File sdRoot = Environment.getExternalStorageDirectory();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String storageDir = prefs.getString(OSMTracker.Preferences.KEY_STORAGE_DIR,	OSMTracker.Preferences.VAL_STORAGE_DIR);
		if (sdRoot.canWrite()) {
			// Create base OSMTracker directory on SD Card
			File osmTrackerDir = new File(sdRoot + storageDir);
			if (!osmTrackerDir.exists()) {
				osmTrackerDir.mkdir();
			}

			// Create track directory
			Date startDate = new Date();
			File trackDir = new File(osmTrackerDir + File.separator + DataHelper.FILENAME_FORMATTER.format(startDate));
			trackDir.mkdir();
			
			// Create entry in TRACK table
			ContentValues values = new ContentValues();
			values.put(Schema.COL_NAME, "");
			values.put(Schema.COL_START_DATE, startDate.getTime());
			values.put(Schema.COL_DIR, trackDir.getAbsolutePath());
			Uri trackUri = getContentResolver().insert(TrackContentProvider.CONTENT_URI_TRACK, values);
			return ContentUris.parseId(trackUri);
		} else {
			throw new CreateTrackException(getResources().getString(R.string.error_externalstorage_not_writable));
		}
	}
	
	/**
	 * Exports a track to a GPX file.
	 * @param trackId Id of the track to export
	 */
	private void exportTrackAsGpx(long trackId) throws ExportTrackException {

		File sdRoot = Environment.getExternalStorageDirectory();
		if (sdRoot.canWrite()) {
			Cursor c = getContentResolver().query(
					ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId),
					null, null, null, null);
			
			c.moveToFirst();
			File trackDir = new File(c.getString(c.getColumnIndex(Schema.COL_DIR)));
			long startDate = c.getLong(c.getColumnIndex(Schema.COL_START_DATE));
			c.close();
			
			if (trackDir != null) {
	
				File trackFile = new File(trackDir, DataHelper.FILENAME_FORMATTER.format(new Date(startDate)) + DataHelper.EXTENSION_GPX);

				Cursor cTrackPoints = getContentResolver().query(
						TrackContentProvider.trackPointsUri(trackId),
						null, null, null, Schema.COL_TIMESTAMP + " asc");
				Cursor cWayPoints = getContentResolver().query(
						TrackContentProvider.waypointsUri(trackId),
						null, null, null,
						Schema.COL_TIMESTAMP + " asc");
	
				try {
					GPXFileWriter.writeGpxFile(getResources(), cTrackPoints, cWayPoints, trackFile,
							PreferenceManager.getDefaultSharedPreferences(this));
				} catch (IOException ioe) {
					throw new ExportTrackException(ioe.getMessage());
				} finally {
					cTrackPoints.close();
					cWayPoints.close();
				}
			}
		} else {
			throw new ExportTrackException(getResources().getString(R.string.error_externalstorage_not_writable));
		}
	}
	
}
