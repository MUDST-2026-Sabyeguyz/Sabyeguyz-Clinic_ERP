<script setup lang="ts">
// Slowly drifting mesh gradient behind the top of every staff page (WebGL gradient). Falls back to the static CSS gradient on the container when WebGL is
// unavailable, and renders a single still frame when the user prefers reduced motion.
import { onBeforeUnmount, onMounted, ref } from 'vue'

const canvas = ref<HTMLCanvasElement | null>(null)

const vertexSource = `
attribute vec2 a_position;
void main() { gl_Position = vec4(a_position, 0.0, 1.0); }
`

const fragmentSource = `
precision mediump float;
uniform vec2 u_resolution;
uniform float u_time;
uniform vec3 u_c1;
uniform vec3 u_c2;
uniform vec3 u_c3;
uniform vec3 u_c4;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(
    mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
    mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x),
    f.y
  );
}
float fbm(vec2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 4; i++) {
    v += a * noise(p);
    p *= 2.0;
    a *= 0.5;
  }
  return v;
}

void main() {
  vec2 uv = gl_FragCoord.xy / u_resolution; // y points up: (0, 1) is the top-left corner
  float aspect = u_resolution.x / u_resolution.y;
  vec2 p = vec2(uv.x * aspect, uv.y) * 1.1;
  float t = u_time * 0.06;
  vec2 q = vec2(fbm(p + vec2(t, 0.0)), fbm(p + vec2(5.2, 1.3) - vec2(0.0, t)));
  // Diagonal ramp: strongest color at the top-left, fading toward the bottom-right.
  float d = uv.x * 0.95 - uv.y * 0.95;
  d += (fbm(p * 0.9 + q * 1.6 + t) - 0.5) * 1.1; // organic warp
  d += 0.1 * sin(uv.x * 5.0 + uv.y * 3.0 + t * 6.0); // soft sweeping waves
  float f = 1.0 - smoothstep(-0.85, 0.55, d);
  vec3 col = mix(u_c1, u_c2, smoothstep(0.0, 0.3, f));
  col = mix(col, u_c3, smoothstep(0.25, 0.6, f));
  col = mix(col, u_c4, smoothstep(0.55, 0.95, f));
  gl_FragColor = vec4(col, 1.0);
}
`

// Clinic green: white -> faint green -> soft green -> medium teal-green.
const colors: Array<[number, number, number]> = [
  [1, 1, 1],
  [0.965, 0.99, 0.98],
  [0.91, 0.968, 0.948],
  [0.82, 0.928, 0.895],
]

let frame = 0
let cleanup: (() => void) | null = null

function compile(gl: WebGLRenderingContext, type: number, source: string) {
  const shader = gl.createShader(type)
  if (!shader) return null
  gl.shaderSource(shader, source)
  gl.compileShader(shader)
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    gl.deleteShader(shader)
    return null
  }
  return shader
}

function start(el: HTMLCanvasElement) {
  const gl = el.getContext('webgl', { antialias: false, alpha: false })
  if (!gl) return
  const vertex = compile(gl, gl.VERTEX_SHADER, vertexSource)
  const fragment = compile(gl, gl.FRAGMENT_SHADER, fragmentSource)
  const program = gl.createProgram()
  if (!vertex || !fragment || !program) return
  gl.attachShader(program, vertex)
  gl.attachShader(program, fragment)
  gl.linkProgram(program)
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) return
  gl.useProgram(program)

  const buffer = gl.createBuffer()
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW)
  const position = gl.getAttribLocation(program, 'a_position')
  gl.enableVertexAttribArray(position)
  gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0)

  const resolution = gl.getUniformLocation(program, 'u_resolution')
  const time = gl.getUniformLocation(program, 'u_time')
  colors.forEach((c, i) => gl.uniform3f(gl.getUniformLocation(program, `u_c${i + 1}`), ...c))

  // The gradient is soft, so render at half size and let CSS scale it up.
  const resize = () => {
    const w = Math.max(2, Math.round(el.clientWidth / 2))
    const h = Math.max(2, Math.round(el.clientHeight / 2))
    if (el.width !== w || el.height !== h) {
      el.width = w
      el.height = h
      gl.viewport(0, 0, w, h)
    }
  }
  const draw = (seconds: number) => {
    resize()
    gl.uniform2f(resolution, el.width, el.height)
    gl.uniform1f(time, seconds)
    gl.drawArrays(gl.TRIANGLES, 0, 3)
  }

  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
  const origin = performance.now()
  let last = 0
  const loop = (now: number) => {
    frame = requestAnimationFrame(loop)
    if (now - last < 66) return // ~15fps is plenty for a slow drift
    last = now
    draw((now - origin) / 1000 + 20)
  }
  // Pause while the user is interacting so clicks never compete with the shader for the GPU.
  let interacting = false
  let idleTimer = 0
  const onInteract = () => {
    interacting = true
    cancelAnimationFrame(frame)
    window.clearTimeout(idleTimer)
    idleTimer = window.setTimeout(() => {
      interacting = false
      run()
    }, 1200)
  }
  const run = () => {
    cancelAnimationFrame(frame)
    if (interacting) return
    if (reduceMotion.matches || document.hidden) {
      if (!document.hidden) draw(20)
      return
    }
    frame = requestAnimationFrame(loop)
  }
  const onResize = () => draw((performance.now() - origin) / 1000 + 20)

  draw(20)
  run()
  document.addEventListener('visibilitychange', run)
  reduceMotion.addEventListener('change', run)
  window.addEventListener('resize', onResize)
  const interactionEvents = ['pointerdown', 'keydown', 'wheel', 'touchstart'] as const
  interactionEvents.forEach((type) =>
    window.addEventListener(type, onInteract, { passive: true, capture: true }),
  )
  cleanup = () => {
    cancelAnimationFrame(frame)
    document.removeEventListener('visibilitychange', run)
    reduceMotion.removeEventListener('change', run)
    window.removeEventListener('resize', onResize)
    window.clearTimeout(idleTimer)
    interactionEvents.forEach((type) => window.removeEventListener(type, onInteract, true))
    gl.getExtension('WEBGL_lose_context')?.loseContext()
  }
}

onMounted(() => {
  if (canvas.value) start(canvas.value)
})
onBeforeUnmount(() => cleanup?.())
</script>

<template>
  <div class="animated-gradient" aria-hidden="true">
    <canvas ref="canvas" class="animated-gradient__canvas"></canvas>
    <div class="animated-gradient__fade"></div>
  </div>
</template>

<style scoped>
.animated-gradient {
  position: absolute;
  z-index: -1;
  top: 0;
  right: 0;
  left: 0;
  height: clamp(520px, 95vh, 860px);
  overflow: hidden;
  pointer-events: none;
  /* Static fallback when WebGL is unavailable. */
  background: linear-gradient(180deg, #eef8f5 0, #fff 100%);
}

.animated-gradient__canvas,
.animated-gradient__fade {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

/* Fades the gradient into the white page. */
.animated-gradient__fade {
  background: linear-gradient(180deg, transparent 45%, #fff 100%);
}

@media print {
  .animated-gradient {
    display: none;
  }
}
</style>
