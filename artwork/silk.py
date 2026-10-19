# Abstract "Silk" wallpaper: dark flowing fabric lit by two soft coloured lights.
# blender -b --factory-startup -P silk.py -- out.png [w h samples]
import bpy, sys, math
from mathutils import Vector
argv = sys.argv[sys.argv.index('--') + 1:]
out = argv[0]; W = int(argv[1]) if len(argv) > 1 else 3840; H = int(argv[2]) if len(argv) > 2 else 2160; spp = int(argv[3]) if len(argv) > 3 else 256
bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene

bpy.ops.mesh.primitive_plane_add(size=1)
cloth = bpy.context.object; cloth.scale = (40, 28, 1); bpy.ops.object.transform_apply(scale=True)
bpy.ops.object.mode_set(mode='EDIT'); bpy.ops.mesh.subdivide(number_cuts=400); bpy.ops.object.mode_set(mode='OBJECT')
for v in cloth.data.vertices:
    x, y = v.co.x, v.co.y
    u = x * 0.8 + y * 0.6
    z = 0.75 * math.sin(u * 1.05 + 0.7 * math.sin(y * 0.45)) + 0.28 * math.sin(u * 2.2 - y * 0.5 + 1.3)
    v.co.z = z * (0.65 + 0.35 * math.sin(x * 0.12 + 0.5))
bpy.ops.object.shade_smooth()
m = bpy.data.materials.new('silk'); m.use_nodes = True; b = m.node_tree.nodes['Principled BSDF']
b.inputs['Base Color'].default_value = (0.012, 0.012, 0.02, 1); b.inputs['Roughness'].default_value = 0.42
b.inputs['Sheen Weight'].default_value = 1.0; b.inputs['Sheen Roughness'].default_value = 0.35
b.inputs['Sheen Tint'].default_value = (0.75, 0.7, 1.0, 1); b.inputs['Coat Weight'].default_value = 0.25
cloth.data.materials.append(m)

def area(name, loc, color, energy, size):
    d = bpy.data.lights.new(name, 'AREA'); d.energy = energy; d.color = color; d.size = size
    o = bpy.data.objects.new(name, d); o.location = loc; sc.collection.objects.link(o)
    o.rotation_euler = (Vector((0, 0, 0)) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler(); o.visible_camera = False; o.visible_glossy = False
area('violet', (-16, 7, 2.5), (0.55, 0.35, 1.0), 9000, 10)
area('amber', (16, -6, 2.0), (1.0, 0.45, 0.2), 6500, 9)
area('blue', (3, 18, 3.0), (0.25, 0.55, 1.0), 4000, 12)
w = bpy.data.worlds.new('w'); sc.world = w; w.use_nodes = True; w.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.0

cd = bpy.data.cameras.new('c'); cam = bpy.data.objects.new('c', cd); sc.collection.objects.link(cam); sc.camera = cam
cam.location = (0.4, -5.5, 8.5); cam.rotation_euler = (Vector((0.2, 0.9, 0)) - cam.location).to_track_quat('-Z', 'Y').to_euler()
cd.lens = 32
r = sc.render; r.engine = 'CYCLES'; r.resolution_x = W; r.resolution_y = H
prefs = bpy.context.preferences.addons['cycles'].preferences; prefs.compute_device_type = 'METAL'; prefs.get_devices()
for dv in prefs.devices: dv.use = True
sc.cycles.device = 'GPU'; sc.cycles.samples = spp; sc.cycles.use_denoising = True
sc.view_settings.view_transform = 'AgX'; sc.view_settings.look = 'AgX - Medium High Contrast'
r.image_settings.file_format = 'PNG'; r.filepath = out
bpy.ops.render.render(write_still=True)
