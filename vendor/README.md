# Android NumPy wheels for 16 KB pages

OpenStar 1.0's NumPy wheel pulled in OpenBLAS and libgfortran built for 4 KB pages. On a 16 KB Android system the linker rejected `libgfortran.so.3`, and Skyfield failed to import after Generate map.

This directory contains NumPy 1.26.2 **build 1**, CPython 3.13 wheels for Android ARM64 and x86-64. They use NumPy's bundled C BLAS/LAPACK routines and do not depend on OpenBLAS or Fortran. All native extensions are linked with `-Wl,-z,max-page-size=16384`. The app also uses Chaquopy 17's Python 3.13 runtime.

`app/build.gradle` adds `vendor/wheels` as a pip source. Build number 1 takes precedence over upstream build 0. Do not remove these wheels or substitute upstream build 0 without checking every transitive native library.

## Sources and build

- NumPy source: https://files.pythonhosted.org/packages/dd/2b/205ddff2314d4eea852e31d53b8e55eb3f32b292efc3dd86bd827ab9019d/numpy-1.26.2.tar.gz
- Chaquopy source: https://github.com/chaquo/chaquopy/tree/3fa61dce7cfc4c220a50dbe5ae71aaaa27753c81
- Android NDK r27d, version `27.3.13750724`, Linux x86-64.
- Android Python target headers/libraries: `com.chaquo.python:target:3.13.9-0`, both ABI ZIPs from https://chaquo.com/maven/com/chaquo/python/target/3.13.9-0/
- Build host: Linux x86-64, CPython 3.13.15. Setuptools 69.0.2 and Cython 3.0.12 are used inside the isolated package build.

The full modified recipe and patches are in `numpy-recipe/`. Changes to the upstream recipe: remove the OpenBLAS host requirement, allow NumPy's bundled reference libraries, set `NPY_BLAS_ORDER` and `NPY_LAPACK_ORDER` to empty strings, and increment the wheel build number. The upstream Android environment already sets 16 KB linker alignment. NumPy's source and bundled C routines retain their BSD and bundled license notices in each wheel and `licenses/`.

To reproduce on Linux, clone the pinned Chaquopy commit with its `server/pypi` and `target` trees. Use a Python 3.13 virtual environment and install the upstream wheel builder's requirements (see `server/pypi/README-old.md`), or the subset used here:

```sh
python -m pip install build==1.5.0 jinja2 jsonschema==2.6.0 pyelftools==0.29 pypi-simple==1.1.0 PyYAML==6.0.3 setuptools==69.1.1 wheel==0.33.6 patchelf==0.19.1 tqdm==4.66.1
```

Install NDK r27d under `$ANDROID_HOME/ndk/27.3.13750724`. Put the two target ZIPs in the clone's `maven/com/chaquo/python/target/3.13.9-0/`. Copy `numpy-recipe/meta.yaml` and `numpy-recipe/patches/` into `server/pypi/packages/numpy/`. Keep all wheel builder files, including `compiler-wrapper.py`.

From `server/pypi`, run:

```sh
NPY_NUM_BUILD_JOBS=8 python build-wheel.py --python 3.13 --abi arm64-v8a numpy
NPY_NUM_BUILD_JOBS=8 python build-wheel.py --python 3.13 --abi x86_64 numpy
```

For WSL, keep the compiler and build tree on the Linux filesystem. The compiler wrapper is a standalone standard-library script; using `#!/usr/bin/python3 -S` as its shebang avoids repeated cross-environment startup overhead. This host-only adjustment does not change the numerical source.

Copy the resulting `dist/numpy/*.whl` into `vendor/wheels/`, then run `tools/check_native_alignment.py` on each wheel and the final APK. Runtime verification on 16 KB Android is also required; ELF alignment alone does not prove the astronomy function runs. This is an app-specific build, not a promise of compatibility with other scientific Python packages.
