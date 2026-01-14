package com.example.voxelserver;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

public class VoxelWebSocketPlugin extends JavaPlugin {
    private VoxelWebSocketServer wsServer;
    
    @Override
    public void onEnable() {
        wsServer = new VoxelWebSocketServer(this, 8887);
        wsServer.start();
        getLogger().info("WebSocket server started on port 8887");
    }
    
    @Override
    public void onDisable() {
        try {
            wsServer.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class VoxelWebSocketServer extends WebSocketServer {
    private final VoxelWebSocketPlugin plugin;
    private final Gson gson = new Gson();
    
    public VoxelWebSocketServer(VoxelWebSocketPlugin plugin, int port) {
        super(new InetSocketAddress(port));
        this.plugin = plugin;
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        plugin.getLogger().info("New connection from " + conn.getRemoteSocketAddress());
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            String type = json.get("type").getAsString();
            
            if ("bulkVoxels".equals(type)) {
                handleBulkVoxels(json);
            } else if ("compressedVoxels".equals(type)) {
                handleCompressedVoxels(json);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Method 1: Array of individual voxels
    private void handleBulkVoxels(JsonObject json) {
        String worldName = json.get("world").getAsString();
        JsonArray voxels = json.getAsJsonArray("voxels");
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World not found: " + worldName);
                return;
            }
            
            Set<org.bukkit.Chunk> affectedChunks = new HashSet<>();
            int count = 0;
            
            for (int i = 0; i < voxels.size(); i++) {
                JsonObject voxel = voxels.get(i).getAsJsonObject();
                int x = voxel.get("x").getAsInt();
                int y = voxel.get("y").getAsInt();
                int z = voxel.get("z").getAsInt();
                String materialName = voxel.get("material").getAsString();
                
                Material material = Material.getMaterial(materialName.toUpperCase());
                if (material == null) continue;
                
                org.bukkit.Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
                affectedChunks.add(chunk);
                
                world.getBlockAt(x, y, z).setType(material, false);
                count++;
            }
            
            // Refresh all affected chunks
            affectedChunks.forEach(chunk -> {
                world.refreshChunk(chunk.getX(), chunk.getZ());
            });
            
            plugin.getLogger().info("Set " + count + " blocks");
        });
    }
    
    // Method 2: Compressed 3D array (more efficient for dense data)
    private void handleCompressedVoxels(JsonObject json) {
        String worldName = json.get("world").getAsString();
        int startX = json.get("startX").getAsInt();
        int startY = json.get("startY").getAsInt();
        int startZ = json.get("startZ").getAsInt();
        int sizeX = json.get("sizeX").getAsInt();
        int sizeY = json.get("sizeY").getAsInt();
        int sizeZ = json.get("sizeZ").getAsInt();
        
        // Decode base64 encoded voxel data (1 byte per block = material ID)
        String dataBase64 = json.get("data").getAsString();
        byte[] voxelData = Base64.getDecoder().decode(dataBase64);
        
        // Optional: palette for more material types
        JsonArray paletteJson = json.has("palette") ? json.getAsJsonArray("palette") : null;
        Material[] palette = null;
        
        if (paletteJson != null) {
            palette = new Material[paletteJson.size()];
            for (int i = 0; i < paletteJson.size(); i++) {
                String matName = paletteJson.get(i).getAsString();
                palette[i] = Material.getMaterial(matName.toUpperCase());
            }
        }
        
        final Material[] finalPalette = palette;
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World not found: " + worldName);
                return;
            }
            
            Set<org.bukkit.Chunk> affectedChunks = new HashSet<>();
            int idx = 0;
            
            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    for (int z = 0; z < sizeZ; z++) {
                        if (idx >= voxelData.length) break;
                        
                        int blockX = startX + x;
                        int blockY = startY + y;
                        int blockZ = startZ + z;
                        
                        // Get material from palette or direct ID
                        Material material;
                        if (finalPalette != null) {
                            int paletteIdx = voxelData[idx] & 0xFF;
                            if (paletteIdx >= finalPalette.length) {
                                idx++;
                                continue;
                            }
                            material = finalPalette[paletteIdx];
                        } else {
                            // Direct material ID (not recommended, use palette)
                            material = getMaterialById(voxelData[idx] & 0xFF);
                        }
                        
                        //if (material == null || material == Material.AIR) {
                        //    idx++;
                        //    continue;
                        //}
                        
                        org.bukkit.Chunk chunk = world.getChunkAt(blockX >> 4, blockZ >> 4);
                        affectedChunks.add(chunk);
                        
                        world.getBlockAt(blockX, blockY, blockZ).setType(material, false);
                        idx++;
                    }
                }
            }
            
            affectedChunks.forEach(chunk -> {
                world.refreshChunk(chunk.getX(), chunk.getZ());
            });
            
            plugin.getLogger().info("Set voxel data: " + sizeX + "x" + sizeY + "x" + sizeZ);
        });
    }
    
    private Material getMaterialById(int id) {
        // Simple mapping - expand as needed
        switch (id) {
            case 0: return Material.AIR;
            case 1: return Material.STONE;
            case 2: return Material.DIRT;
            case 3: return Material.GRASS_BLOCK;
            case 4: return Material.COBBLESTONE;
            case 5: return Material.OAK_PLANKS;
            default: return Material.STONE;
        }
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        plugin.getLogger().info("Connection closed: " + conn.getRemoteSocketAddress());
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        plugin.getLogger().warning("WebSocket error: " + ex.getMessage());
    }
    
    @Override
    public void onStart() {
        plugin.getLogger().info("WebSocket server started successfully");
    }
}
