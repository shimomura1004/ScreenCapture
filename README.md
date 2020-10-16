# Tested environment
- Android 10, 11
- API level 29

# How to test
Run the app and tap "start", then run the command on PC:
```
ffplay -framerate 60 -analyzeduration 100 -i tcp://(target):8080
```

![screenshot](https://user-images.githubusercontent.com/29224/96333232-a6422b00-10a3-11eb-9a37-c356503f37f3.png)
