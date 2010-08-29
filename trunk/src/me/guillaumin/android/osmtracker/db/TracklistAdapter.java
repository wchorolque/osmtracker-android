package me.guillaumin.android.osmtracker.db;

import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import me.guillaumin.android.osmtracker.R;
import me.guillaumin.android.osmtracker.db.TrackContentProvider.Schema;
import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

/**
 * Adapter for track list in Track Manager
 * 
 * @author Nicolas Guillaumin
 *
 */
public class TracklistAdapter extends CursorAdapter {

	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE dd/MM/yyyy, HH:mm:ss");
	
	public TracklistAdapter(Context context, Cursor c) {
		super(context, c);
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		bind(cursor, view, context);	
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup vg) {
		View view = LayoutInflater.from(vg.getContext()).inflate(R.layout.tracklist_item,
				vg, false);
		return bind(cursor, view, context);
	}
	
	/**
	 * Do the binding between data and item view.
	 * 
	 * @param cursor
	 *            Cursor to pull data
	 * @param v
	 *            RelativeView representing one item
	 * @param context
	 *            Context, to get resources
	 * @return The relative view with data bound.
	 */
	private View bind(Cursor cursor, View v, Context context) {
		TextView vId = (TextView) v.findViewById(R.id.trackmgr_item_id);
		TextView vStartDate = (TextView) v.findViewById(R.id.trackmgr_item_startdate);
		TextView vWps = (TextView) v.findViewById(R.id.trackmgr_item_wps);
		TextView vTps = (TextView) v.findViewById(R.id.trackmgr_item_tps);

		// Bind id
		long trackId = cursor.getLong(cursor.getColumnIndex(Schema.COL_ID));
		String strTrackId = Long.toString(trackId);
		vId.setText("#" + strTrackId);

		// Bind start date
		long startDate = cursor.getLong(cursor.getColumnIndex(Schema.COL_START_DATE));
		vStartDate.setText(DateFormat.getDateTimeInstance().format(new Date(startDate)));
		
		// Bind WP count
		Cursor wpCursor = context.getContentResolver().query(
				TrackContentProvider.trackPointsUri(trackId),
				null, null,	null, null);
		vWps.setText(Integer.toString(wpCursor.getCount()));
		wpCursor.close();

		// Bind TP count
		Cursor tpCursor = context.getContentResolver().query(
				TrackContentProvider.waypointsUri(trackId),
				null, null,	null, null);
		vTps.setText(Integer.toString(tpCursor.getCount()));
		tpCursor.close();

		return v;
	}

}
