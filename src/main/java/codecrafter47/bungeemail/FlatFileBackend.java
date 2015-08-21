package codecrafter47.bungeemail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.SneakyThrows;
import lombok.Synchronized;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

/**
 * Created by florian on 15.11.14.
 */
public class FlatFileBackend implements IStorageBackend, Listener {

    private BungeeMail plugin;
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private File saveFile;
    private File tmpSaveFile;
    private Data data;

    public FlatFileBackend(BungeeMail plugin) {
        this.plugin = plugin;
        saveFile = new File(plugin.getDataFolder(), "data.json");
        tmpSaveFile = new File(plugin.getDataFolder(), "data.json.tmp");
        readData();
        plugin.getProxy().getPluginManager().registerListener(plugin, this);
    }

    @SneakyThrows
    @Synchronized
    private void readData() {
        if (saveFile.exists()) {
            try {
                FileReader fileReader = new FileReader(saveFile);
                data = gson.fromJson(fileReader, Data.class);
                fileReader.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                data = new Data();
            }
        } else {
            data = new Data();
        }
    }

    @SneakyThrows
    @Synchronized
    private void saveData() {
        if(tmpSaveFile.exists()){
            tmpSaveFile.delete();
        }
        tmpSaveFile.createNewFile();
        FileWriter fileWriter = new FileWriter(tmpSaveFile);
        gson.toJson(data, fileWriter);
        fileWriter.close();
        if (saveFile.exists()) {
            saveFile.delete();
        }
        tmpSaveFile.renameTo(saveFile);
    }

    @Synchronized
    @Override
    public List<Message> getMessagesFor(UUID uuid, boolean onlyNew) {
        ArrayList<Message> messages = new ArrayList<>();
        for (Message message : data.data) {
            if (message.getRecipient().equals(uuid) && (!message.isRead() || !onlyNew)) messages.add(message);
        }
        return messages;
    }

    @Synchronized
    @Override
    public void saveMessage(Message message) {
        if (!data.data.contains(message)) {
            data.data.add(message);
        }
        saveData();
    }

    @Synchronized
    @Override
    public void markRead(Message message) {
        message.setRead(true);
        saveData();
    }

    @Synchronized
    @Override
    public void delete(Message message) {
        data.data.remove(message);
        saveData();
    }

    @Synchronized
    @Override
    public void delete(int id) {
        Iterator<Message> iterator = data.data.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().hashCode() == id) {
                iterator.remove();
            }
        }
        saveData();
    }

    @Override
    @Synchronized
    public void deleteOlder(long time, boolean deleteUnread) {
        for (Iterator<Message> iterator = data.data.iterator(); iterator.hasNext(); ) {
            Message message = iterator.next();
            if (message.getTime() < time && (deleteUnread || message.isRead())) {
                iterator.remove();
            }
        }
        saveData();
    }

    @Synchronized
    @Override
    public UUID getUUIDForName(String name) {
        return data.uuidMap.get(name);
    }

    @Synchronized
    @Override
    public Collection<UUID> getAllKnownUUIDs() {
        return data.uuidMap.values();
    }

    @Synchronized
    @Override
    public Collection<String> getKnownUsernames() {
        return data.uuidMap.keySet();
    }

    @Synchronized
    @EventHandler
    public void onJoin(PostLoginEvent event) {
        data.uuidMap.put(event.getPlayer().getName(), event.getPlayer().getUniqueId());
        saveData();
    }

    private static class Data {
        private List<Message> data = new ArrayList<>();
        private Map<String, UUID> uuidMap = new HashMap<>();
    }
}
