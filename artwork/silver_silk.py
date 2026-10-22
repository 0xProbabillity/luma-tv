# "Silver Silk" wallpapers: the Silk drape in pale silver for the day and graphite silver for the night.
# blender -b --factory-startup -P silver_silk.py -- day|night out.png [w h samples]
import bpy, sys, math
from mathutils import Vector
argv = sys.argv[sys.argv.index('--') + 1:]
tone, out = argv[0], argv[1]
W = int(argv[2]) if len(argv) > 2 else 3840; H = int(argv[3]) if len(argv) > 3 else 2160; spp = int(argv[4]) if len(argv) > 4 else 256
day = tone == 'day'
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
# Grazing light on a faceted mesh draws short dark slashes (the shadow terminator): smooth it and offset the terminator.
sub = cloth.modifiers.new('smooth', 'SUBSURF'); sub.levels = 0; sub.render_levels = 2
cloth.shadow_terminator_geometry_offset = 0.6; cloth.shadow_terminator_normal_offset = 0.2
m = bpy.data.materials.new('silk'); m.use_nodes = True; b = m.node_tree.nodes['Principled BSDF']
if day:
    b.inputs['Base Color'].default_value = (0.72, 0.73, 0.75, 1); b.inputs['Roughness'].default_value = 0.38
    b.inputs['Sheen Weight'].default_value = 0.8; b.inputs['Sheen Roughness'].default_value = 0.3
    b.inputs['Sheen Tint'].default_value = (1.0, 1.0, 1.0, 1); b.inputs['Coat Weight'].default_value = 0.35
    b.inputs['Coat Roughness'].default_value = 0.25
else:
    b.inputs['Base Color'].default_value = (0.018, 0.019, 0.022, 1); b.inputs['Roughness'].default_value = 0.36
    b.inputs['Sheen Weight'].default_value = 1.0; b.inputs['Sheen Roughness'].default_value = 0.32
    b.inputs['Sheen Tint'].default_value = (0.86, 0.9, 1.0, 1); b.inputs['Coat Weight'].default_value = 0.4
    b.inputs['Coat Roughness'].default_value = 0.22
cloth.data.materials.append(m)

def area(name, loc, color, energy, size):
    d = bpy.data.lights.new(name, 'AREA'); d.energy = energy; d.color = color; d.size = size
    o = bpy.data.objects.new(name, d); o.location = loc; sc.collection.objects.link(o)
    o.rotation_euler = (Vector((0, 0, 0)) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler(); o.visible_camera = False; o.visible_glossy = False
if day:
    area('key', (-16, 7, 1.6), (1.0, 0.99, 0.97), 16000, 10)
    area('fill', (16, -6, 1.8), (0.93, 0.96, 1.0), 6000, 10)
    area('back', (3, 18, 2.4), (1.0, 1.0, 1.0), 5000, 12)
else:
    area('key', (-16, 7, 2.5), (0.9, 0.93, 1.0), 9000, 10)
    area('rim', (16, -6, 2.0), (1.0, 0.97, 0.94), 3800, 9)
    area('blue', (3, 18, 3.0), (0.7, 0.8, 1.0), 2400, 12)
w = bpy.data.worlds.new('w'); sc.world = w; w.use_nodes = True
w.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.12 if day else 0.0
w.node_tree.nodes['Background'].inputs['Color'].default_value = (0.9, 0.92, 0.95, 1)

cd = bpy.data.cameras.new('c'); cam = bpy.data.objects.new('c', cd); sc.collection.objects.link(cam); sc.camera = cam
cam.location = (0.4, -5.5, 8.5); cam.rotation_euler = (Vector((0.2, 0.9, 0)) - cam.location).to_track_quat('-Z', 'Y').to_euler()
cd.lens = 32
r = sc.render; r.engine = 'CYCLES'; r.resolution_x = W; r.resolution_y = H
prefs = bpy.context.preferences.addons['cycles'].preferences; prefs.compute_device_type = 'METAL'; prefs.get_devices()
for dv in prefs.devices: dv.use = True
sc.cycles.device = 'GPU'; sc.cycles.samples = spp; sc.cycles.use_denoising = True
sc.view_settings.view_transform = 'AgX'; sc.view_settings.look = 'AgX - Base Contrast' if day else 'AgX - Medium High Contrast'
r.image_settings.file_format = 'PNG'; r.filepath = out
bpy.ops.render.render(write_still=True)
