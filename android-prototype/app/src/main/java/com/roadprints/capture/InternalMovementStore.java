package com.roadprints.capture;
import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
/** Recoverable raw internal capture, deliberately outside ordinary journey/progress stores. */
final class InternalMovementStore {
    static void save(Context app,JSONObject evidence) throws Exception {
        File directory=new File(app.getFilesDir(),"destination-internal-evidence");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot retain internal evidence");
        String id=evidence.getString("journey_id");
        if(!id.matches("[a-zA-Z0-9-]+"))throw new IOException("Invalid evidence identity");
        AtomicFile file=new AtomicFile(new File(directory,id+".json"));
        FileOutputStream out=null;
        try {out=file.startWrite();out.write(evidence.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(Exception error){if(out!=null)file.failWrite(out);throw error;}
    }
}
