package com.roadprints.capture;

import android.content.Context;
import android.graphics.PorterDuff;
import android.widget.ImageView;
import java.util.Locale;

/** Shared Roadprints outline suite: 24-unit viewport, two-unit rounded strokes. */
final class RoadprintsIcons {
    static int transport(String mode) {
        switch (mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT)) {
            case "driving":return R.drawable.ic_roadprints_car;
            case "walking":case "running":case "pedestrian":return R.drawable.ic_roadprints_foot;
            case "bus":return R.drawable.ic_roadprints_bus;
            case "train":return R.drawable.ic_roadprints_train;
            case "plane":return R.drawable.ic_roadprints_plane;
            case "cycling":case "bicycle":return R.drawable.ic_roadprints_bicycle;
            case "ferry":return R.drawable.ic_roadprints_ferry;
            case "all":return R.drawable.ic_roadprints_all;
            default:return R.drawable.ic_roadprints_unknown;
        }
    }
    static int achievement(String id) {
        if(id==null)return R.drawable.ic_roadprints_star;
        if(id.startsWith("foot-"))return R.drawable.ic_roadprints_foot;
        if(id.startsWith("road-"))return R.drawable.ic_roadprints_car;
        switch(id) {
            case "county-collector":return R.drawable.ic_nav_map;
            case "sat-nav-on":return R.drawable.ic_roadprints_car;
            case "picasso":case "joining-the-dots":return R.drawable.ic_roadprints_star;
            case "mastered-monopoly":return R.drawable.ic_roadprints_train;
            case "the-knowledge":return R.drawable.ic_roadprints_road;
            case "m62-summit":return R.drawable.ic_roadprints_mountain;
            case "mary-high-streets":return R.drawable.ic_roadprints_crown;
            case "angel-of-the-north":return R.drawable.ic_roadprints_angel;
            case "stonehenge-solstice":return R.drawable.ic_roadprints_stones;
            case "groundhog-day":return R.drawable.ic_roadprints_repeat;
            case "sightseer":return R.drawable.ic_roadprints_binoculars;
            case "big-ben":case "blackpool-tower":return R.drawable.ic_roadprints_clocktower;
            case "conwy-castle":case "stormont":return R.drawable.ic_roadprints_castle;
            case "windsor-castle":return R.drawable.ic_roadprints_crown;
            case "bullring-bull":return R.drawable.ic_roadprints_bull;
            case "ness-point":return R.drawable.ic_nav_map;
            case "loch-ness":return R.drawable.ic_roadprints_lake;
            case "humber-bridge-landmark":case "trent-bridge":
            case "spanning-the-nation":return R.drawable.ic_roadprints_bridge;
            case "service-moneybags":return R.drawable.ic_roadprints_wallet;
            case "service-being-posh":return R.drawable.ic_roadprints_hat;
            case "service-first-stop":case "service-ten-stops":return R.drawable.ic_roadprints_fuel;
            case "motorway-complete":return R.drawable.ic_roadprints_star;
            default:return R.drawable.ic_roadprints_road;
        }
    }
    static ImageView image(Context context,int resource,int color,String description) {
        ImageView view=new ImageView(context);view.setImageResource(resource);
        view.setColorFilter(color,PorterDuff.Mode.SRC_IN);view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setContentDescription(description);return view;
    }
}
