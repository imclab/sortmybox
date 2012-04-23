#!/usr/bin/env python
namespaces = {
        'prod'   : 'prod',
        'staging' : 'staging'
}

def get_branch():
    import os
    return os.popen("git symbolic-ref -q HEAD") \
             .read() \
             .rstrip() \
             .split('/')[-1]

def main():
    src = 'war/WEB-INF/appengine-web.xml.format'
    target = 'war/WEB-INF/appengine-web.xml'
    branch = get_branch()
    namespace = 'staging'
    try:
        namespace = namespaces[get_branch()]
    except KeyError:
        pass


    def format(line):
        return line.format(namespace=namespace)
    with open(src, 'r') as src_f:
        with open(target, 'w') as target_f:
            target_f.write('<!-- Autogenerated file do not edit. Edit %s and run "make all". -->\n' % src)
            target_f.writelines(map(format, src_f.readlines()))

    print 'Updating appengine-web.xml with for branch: "%s" namespace: "%s"' % \
          (branch, namespace)

if __name__ == '__main__':
    main();
