# papermc voxel streaming plugin

<img src="/demo.jpg?raw=true" width="512">

By default listens for websocket messages on port 8887. This can be changed in the src java file.
  
A pre-built jar is included, but to build yourself or build modified version:   
```mvn package```    
Then place the built jar in target to your papermc plugins folder
   
```json
{
  "type": "compressedVoxels",
  "world": "world",
  "startX": 0,
  "startY": 64,
  "startZ": 0,
  "sizeX": 3,
  "sizeY": 3,
  "sizeZ": 3,
  "data": "AQEBAgICAwMDAQEBAgICAwMDAQEBAgICAwMD",
  "palette": [
    "air",
    "stone",
    "dirt",
    "oak_planks"
  ]
}
```

Which can be generated for example by the following python program:

```python
import base64
import json

# Volume size
size_x = size_y = size_z = 3

# World-space origin
start_x, start_y, start_z = 0, 64, 0

# Palette (index -> material)
palette = [
    "air",        # 0
    "stone",      # 1
    "dirt",       # 2
    "oak_planks"  # 3
]

# Build voxel data in x -> y -> z order
voxel_bytes = bytearray()

for x in range(size_x):
    for y in range(size_y):
        for z in range(size_z):
            if y == 0:
                voxel_bytes.append(1)  # stone
            elif y == 1:
                voxel_bytes.append(2)  # dirt
            elif y == 2:
                voxel_bytes.append(3)  # oak_planks

# Base64 encode
data_base64 = base64.b64encode(voxel_bytes).decode("ascii")

# Final message
message = {
    "type": "compressedVoxels",
    "world": "world",
    "startX": start_x,
    "startY": start_y,
    "startZ": start_z,
    "sizeX": size_x,
    "sizeY": size_y,
    "sizeZ": size_z,
    "data": data_base64,
    "palette": palette
}

print(json.dumps(message, indent=2))

```
