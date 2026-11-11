package com.roadprints.capture;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.Granularity;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;
import java.util.function.Consumer;
import java.util.function.Function;

/** Holds at most one fix request and ignores callbacks after leaving the map. */
final class MapLocationRequest {
    private CancellationTokenSource pending;

    @SuppressLint("MissingPermission") // The map checks both foreground permissions before requesting.
    static Task<Location> currentLocation(Context context,boolean precise,CancellationTokenSource token){
        return LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(
                new CurrentLocationRequest.Builder()
                        .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                        .setPriority(precise?Priority.PRIORITY_HIGH_ACCURACY:Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                        .setMaxUpdateAgeMillis(0)
                        .setDurationMillis(12_000)
                        .build(),token.getToken());
    }

    void start(Function<CancellationTokenSource,Task<Location>> requester,Consumer<Location> result){
        if(pending!=null)return;
        CancellationTokenSource token=new CancellationTokenSource();
        pending=token;
        try{
            requester.apply(token).addOnCompleteListener(task->{
                if(pending!=token)return;
                pending=null;
                result.accept(task.isSuccessful()?task.getResult():null);
            });
        }catch(SecurityException|IllegalStateException unavailable){
            pending=null;
            result.accept(null);
        }
    }

    void cancel(){
        CancellationTokenSource token=pending;
        pending=null;
        if(token!=null)token.cancel();
    }
}
