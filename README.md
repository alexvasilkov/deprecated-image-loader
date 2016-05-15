### Deprecated ###

This library is not supported, use other libraries for image loading.

### Fluffy ImageLoader ###

Simple task of loading image from Internet into the ImageView is not so simple on Android since you have to worry about multithreading, local caching (on disk and in memory), ListView views recycling and so on.  
This library is intended to provide a simple way of doing all that stuff.

#### Usage ####

First of all you should initialize ImageLoader instance inside your Application class:

    public final class MyApplication extends Application {
        @Override
        public void onCreate() {
            super.onCreate();
            ImagesLoader.create(this);
        }
    }

And now you can start using it everywhere you want:

    ImagesLoader.get().loadImage(imageView, imageProgressView, url, true);

Available methods:

    loadImage(ImageView view, String uri);

    loadImage(ImageView view, View progressView, String uri) {

    loadImage(ImageView view, View progressView, String uri, boolean useDelay, boolean useAnimation);

    loadImage(ImageView view, String uri, boolean useDelay, boolean useAnimation);

    loadImage(ImageView view, String uri, int emptyImageRes, boolean useDelay, boolean useAnimation);

    loadImage(ImageView view, View progressView, String uri, int emptyImageRes, boolean useDelay, boolean useAnimation);

Methods summary:

> Loads image from `uri` into given `view`.  
> Shows `progressView` instead of `view` while loading (if `progressView` is not null).  
> Uses `emptyImageRes` (if not equals to NO_IMAGE_RES_ID) as image placeholder while loading.  
> Optionally you can apply small delay before starting loading image (sometimes usefull for ListView images) and a fade-in animation when image is loaded into the given `view`.

Also you can fine tune loading by using this method:

    loadImage(ImageSpecs specs);

Example:

    ImageSpecs specs = ImageSpecs.getImageSpecsFromView(imageView, uri);
    specs.setDelay(200);
    specs.setDisplayImageWhileProgress(true);
    specs.setProgressView(progressView);
    specs.setUseDiskCache(false);
    specs.setUseMemoryCache(false);
    specs.setWithAnimation(true);
    specs.setOnResourceLoadingListener(new OnResourceLoadingListener<Bitmap>() {
        @Override
        public void onStart(ResourceSpecs<Bitmap> specs) {
        }
        @Override
        public void onPrepare(ResourceSpecs<Bitmap> specs) {
        }
        @Override
        public void onLoaded(ResourceSpecs<Bitmap> specs, Bitmap res, boolean fromMemory, boolean fromDisk) {
        }
    });
    ImagesLoader.get().loadImage(specs);

Uri parameter can have following schemes:  
* http://, https://
* file:// (for local images loading)
* content:// (for images from content providers, i.e. contacts)
* android.resource:// (i.e. "android.resource://" + context.getPackageName() + '/' + R.raw.image)

#### How to build ####

You need [Maven](http://maven.apache.org/) to build the project. Just run `mvn clean install` from project's root, jar file will be generated into `target` folder.

#### License ####

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
